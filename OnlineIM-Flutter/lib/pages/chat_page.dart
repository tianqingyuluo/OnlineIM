import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../protocol/preview_text.dart';
import '../protocol/protocol_engine.dart';
import '../state/im_session.dart';
import '../state/providers.dart';
import '../widgets/friend_picker.dart';
import '../widgets/group_settings_sheet.dart';
import '../widgets/message_bubble.dart';
import '../widgets/safety_number_sheet.dart';

class ChatPage extends ConsumerStatefulWidget {
  const ChatPage({super.key, required this.conversationId});

  final String conversationId;

  @override
  ConsumerState<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends ConsumerState<ChatPage> {
  final _input = TextEditingController();
  final _search = TextEditingController();
  String? _replyTo;
  String? _editing;
  var _showEmoji = false;
  var _searching = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(imSessionProvider).openConversation(widget.conversationId));
  }

  @override
  void dispose() {
    _input.dispose();
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(imSessionProvider);
    final conversation = session.engine.conversations[widget.conversationId];
    final messages = session.engine.history(widget.conversationId);
    AnnouncementRecord? announcement;
    for (final item in session.engine.announcements.values) {
      if (item.groupId == widget.conversationId && !item.deleted && !item.readByMe) {
        announcement = item;
        break;
      }
    }
    final banner = announcement;
    final group = widget.conversationId.startsWith('grp_') || conversation?.type == 'group';
    return Scaffold(
      appBar: AppBar(
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(conversation?.name.isNotEmpty == true ? conversation!.name : widget.conversationId),
          if (!group)
            Text(
              session.engine.presence[conversation?.peerId]?.online == true ? '在线' : '离线',
              style: const TextStyle(fontSize: 12),
            ),
        ]),
        actions: [
          if (!group) ...[
            IconButton(key: const Key('call-audio'), onPressed: () => _call(session, 'audio'), icon: const Icon(Icons.call)),
            IconButton(key: const Key('call-video'), onPressed: () => _call(session, 'video'), icon: const Icon(Icons.videocam)),
          ],
          IconButton(
            key: const Key('chat-search'),
            onPressed: () => setState(() => _searching = !_searching),
            icon: const Icon(Icons.search),
          ),
          PopupMenuButton<String>(
            key: const Key('chat-menu'),
            onSelected: (value) => _menu(session, value),
            itemBuilder: (context) => [
              if (!group) CheckedPopupMenuItem(value: 'e2ee', checked: conversation?.e2eeEnabled == true, child: const Text('端到端加密')),
              if (!group) const PopupMenuItem(value: 'safety', child: Text('查看安全码')),
              if (group) const PopupMenuItem(value: 'group-settings', child: Text('群设置')),
            ],
          ),
        ],
      ),
      body: Column(children: [
        if (_searching)
          Padding(
            padding: const EdgeInsets.all(8),
            child: TextField(
              key: const Key('inchat-search'),
              controller: _search,
              decoration: const InputDecoration(hintText: '搜索会话内消息'),
              onSubmitted: (value) => session.search(value, conversationId: widget.conversationId),
            ),
          ),
        if (banner != null)
          MaterialBanner(
            key: const Key('announcement-banner'),
            content: Text('${banner.title} ${banner.content}'),
            actions: [
              TextButton(
                key: const Key('announcement-read'),
                onPressed: () => session.confirmAnnouncement(widget.conversationId, banner.announcementId),
                child: const Text('我知道了'),
              ),
              TextButton(
                onPressed: () async {
                  await session.loadAnnouncementReaders(widget.conversationId, banner.announcementId);
                  if (!context.mounted) return;
                  final readers = session.announcementReaders?['readers'];
                  await showModalBottomSheet<void>(
                    context: context,
                    builder: (context) => ListView(
                      children: [
                        const ListTile(title: Text('已读成员')),
                        if (readers is List)
                          for (final reader in readers.whereType<Map>())
                            ListTile(title: Text(reader['nickname']?.toString() ?? reader['user_id'].toString())),
                      ],
                    ),
                  );
                },
                child: const Text('已读名单'),
              ),
            ],
          ),
        Expanded(
          child: ListView(
            children: [
              for (final message in messages)
                MessageBubble(
                  message: message,
                  mine: message.senderId == session.selfId,
                  onLongPress: () => _actions(session, message),
                  onQuoteTap: () => _jump(message.replyTo?.messageId),
                  onRedial: () => _call(session, _callType(message)),
                ),
            ],
          ),
        ),
        if (_replyTo != null)
          ListTile(
            key: const Key('quote-banner'),
            title: Text('回复 ${_preview(_replyTo!)}'),
            trailing: IconButton(onPressed: () => setState(() => _replyTo = null), icon: const Icon(Icons.close)),
          ),
        if (_editing != null)
          const ListTile(key: Key('edit-banner'), title: Text('编辑消息'), dense: true),
        Row(children: [
          IconButton(key: const Key('emoji-button'), onPressed: () => setState(() => _showEmoji = !_showEmoji), icon: const Icon(Icons.emoji_emotions_outlined)),
          IconButton(
            key: const Key('image-button'),
            onPressed: () async {
              final picked = await session.picker?.pickImage();
              if (picked != null) await session.sendMedia(widget.conversationId, picked);
            },
            icon: const Icon(Icons.image_outlined),
          ),
          IconButton(
            key: const Key('file-button'),
            onPressed: () async {
              final picked = await session.picker?.pickFile();
              if (picked != null) await session.sendMedia(widget.conversationId, picked);
            },
            icon: const Icon(Icons.attach_file),
          ),
          IconButton(
            key: const Key('voice-button'),
            onPressed: () async {
              final allowed = await session.permissions.ensureMicrophone();
              if (!allowed) return;
              final picked = await session.picker?.recordVoice();
              if (picked != null) await session.sendMedia(widget.conversationId, picked);
            },
            icon: const Icon(Icons.mic_none),
          ),
          if (group)
            IconButton(
              key: const Key('mention-button'),
              onPressed: () => _mention(session),
              icon: const Icon(Icons.alternate_email),
            ),
          Expanded(
            child: TextField(
              key: const Key('chat-input'),
              controller: _input,
              decoration: const InputDecoration(hintText: '输入消息'),
              onChanged: (value) => session.noteDraft(widget.conversationId, value),
            ),
          ),
          IconButton(
            key: const Key('chat-send'),
            onPressed: () async {
              final text = _input.text;
              if (_editing != null) {
                await session.edit(_editing!, text);
                setState(() => _editing = null);
              } else {
                final at = group && text.contains('@所有人') ? <String>['all'] : <String>[];
                await session.sendText(widget.conversationId, text, replyTo: _replyTo, atUsers: at);
                setState(() => _replyTo = null);
              }
              _input.clear();
            },
            icon: const Icon(Icons.send),
          ),
        ]),
        if (_showEmoji) _emojiPanel(session),
      ]),
    );
  }

  Widget _emojiPanel(ImSession session) {
    final items = session.emojis;
    return SizedBox(
      key: const Key('emoji-panel'),
      height: 180,
      child: GridView.count(
        crossAxisCount: 5,
        children: [
          for (final emoji in items.take(100))
            GestureDetector(
              key: Key('emoji-${emoji['emoji_id']}'),
              onLongPress: () => session.deleteEmoji(emoji['emoji_id'].toString()),
              onTap: () => session.sendText(widget.conversationId, emoji['emoji_id'].toString()),
              child: Center(child: Text(emoji['name']?.toString() ?? '表情', overflow: TextOverflow.ellipsis)),
            ),
          const Center(child: Text('上限 100')),
        ],
      ),
    );
  }

  Future<void> _actions(ImSession session, MessageRecord message) async {
    final sentAt = DateTime.fromMillisecondsSinceEpoch(message.timestampMs ?? session.now().millisecondsSinceEpoch);
    final own = message.senderId == session.selfId;
    final group = widget.conversationId.startsWith('grp_');
    final recall = canRecall(
      own: own,
      group: group,
      myRole: session.engine.conversations[widget.conversationId]?.myRole,
      sentAt: sentAt,
      now: session.now(),
      alreadyRecalled: message.isRecalled,
    );
    final edit = canEdit(own: own, messageType: message.messageType, sentAt: sentAt, now: session.now(), recalled: message.isRecalled);
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(title: const Text('回复'), onTap: () => Navigator.pop(context, 'reply')),
          ListTile(title: const Text('转发'), onTap: () => Navigator.pop(context, 'forward')),
          if (edit) ListTile(title: const Text('编辑'), onTap: () => Navigator.pop(context, 'edit')),
          if (recall) ListTile(title: const Text('撤回'), onTap: () => Navigator.pop(context, 'recall')),
        ]),
      ),
    );
    if (!mounted || action == null) return;
    if (action == 'reply') setState(() => _replyTo = message.messageId);
    if (action == 'edit') {
      setState(() => _editing = message.messageId);
      _input.text = message.localPlaintext ?? message.content;
    }
    if (action == 'recall') {
      final ok = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('撤回这条消息？'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('撤回')),
          ],
        ),
      );
      if (ok == true) await session.recall(message.messageId);
    }
    if (action == 'forward') await _forward(session, message.messageId);
  }

  Future<void> _forward(ImSession session, String messageId) async {
    final targets = <({String id, String label})>[
      for (final friend in session.friends)
        if (friendIdOf(friend).isNotEmpty) (id: friendIdOf(friend), label: friendLabelOf(friend)),
      for (final group in session.groups)
        if ((group['group_id']?.toString() ?? '').isNotEmpty)
          (id: group['group_id'].toString(), label: group['name']?.toString().isNotEmpty == true ? group['name'].toString() : '群聊'),
    ];
    if (!mounted) return;
    final selected = await showDialog<List<String>>(
      context: context,
      builder: (context) => _ForwardDialog(targets: targets),
    );
    if (selected == null || selected.isEmpty) return;
    await session.forward(sourceConversationId: widget.conversationId, messageIds: [messageId], targets: selected);
  }

  Future<void> _mention(ImSession session) async {
    final picked = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(key: const Key('mention-all'), title: const Text('@所有人'), onTap: () => Navigator.pop(context, 'all')),
          for (final friend in session.friends)
            ListTile(
              title: Text(friend['nickname']?.toString() ?? ''),
              onTap: () => Navigator.pop(context, friend['nickname']?.toString() ?? ''),
            ),
        ]),
      ),
    );
    if (picked == null) return;
    _input.text = '${_input.text}${picked == 'all' ? '@所有人 ' : '@$picked '}';
    await session.noteDraft(widget.conversationId, _input.text);
  }

  Future<void> _menu(ImSession session, String value) async {
    if (value == 'e2ee') {
      final enabled = session.engine.conversations[widget.conversationId]?.e2eeEnabled == true;
      await session.toggleE2ee(widget.conversationId, !enabled);
    }
    if (!mounted) return;
    if (value == 'group-settings') {
      await showGroupSettings(
        context,
        session,
        groupId: widget.conversationId,
        name: session.engine.conversations[widget.conversationId]?.name,
      );
    }
    if (value == 'safety') {
      final code = await session.safetyCode(widget.conversationId);
      if (!mounted || code == null) return;
      final peer = session.engine.conversations[widget.conversationId]?.peerId ?? '';
      final fingerprint = session.peerKeys[peer]?['fingerprint']?.toString() ?? '';
      await showSafetyNumberSheet(
        context,
        digits: code,
        verified: session.verifiedFingerprints[peer] == fingerprint && fingerprint.isNotEmpty,
        onVerify: () => session.markVerified(peer, fingerprint),
      );
    }
  }

  Future<void> _call(ImSession session, String type) async {
    await session.startCall(widget.conversationId, type);
    if (mounted) context.push('/call');
  }

  void _jump(String? messageId) {
    if (messageId == null) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('跳转到 $messageId')));
  }

  String _preview(String messageId) {
    final message = ref.read(imSessionProvider).engine.messages[messageId];
    if (message == null) return messageId;
    if (message.isRecalled) return '原消息已撤回';
    return message.localPlaintext ?? message.content;
  }

  String _callType(MessageRecord message) {
    if (message.content.contains('audio')) return 'audio';
    return 'video';
  }
}

class _ForwardDialog extends StatefulWidget {
  const _ForwardDialog({required this.targets});

  final List<({String id, String label})> targets;

  @override
  State<_ForwardDialog> createState() => _ForwardDialogState();
}

class _ForwardDialogState extends State<_ForwardDialog> {
  static const _maxTargets = 9;
  final _selected = <String>[];

  void _toggle(String id, bool checked) {
    setState(() {
      if (checked) {
        if (_selected.length >= _maxTargets || _selected.contains(id)) return;
        _selected.add(id);
      } else {
        _selected.remove(id);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      key: const Key('forward-dialog'),
      title: const Text('转发到'),
      content: SizedBox(
        width: double.maxFinite,
        height: 320,
        child: widget.targets.isEmpty
            ? const Center(child: Text('没有可转发的会话'))
            : ListView(
                children: [
                  for (final target in widget.targets)
                    CheckboxListTile(
                      key: Key('forward-target-${target.id}'),
                      value: _selected.contains(target.id),
                      title: Text(target.label),
                      controlAffinity: ListTileControlAffinity.leading,
                      contentPadding: EdgeInsets.zero,
                      onChanged: (checked) => _toggle(target.id, checked == true),
                    ),
                ],
              ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
        FilledButton(
          key: const Key('forward-send'),
          onPressed: _selected.isEmpty ? null : () => Navigator.pop(context, List<String>.from(_selected)),
          child: const Text('发送'),
        ),
      ],
    );
  }
}
