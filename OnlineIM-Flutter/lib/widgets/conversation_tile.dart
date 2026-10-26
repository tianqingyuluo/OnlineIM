import 'package:flutter/material.dart';

import '../protocol/preview_text.dart';
import '../protocol/protocol_engine.dart';

class ConversationTile extends StatelessWidget {
  const ConversationTile({
    super.key,
    required this.conversation,
    required this.onTap,
    this.onPin,
    this.onMute,
    this.onDelete,
    this.online = false,
  });

  final ConversationRecord conversation;
  final VoidCallback onTap;
  final VoidCallback? onPin;
  final VoidCallback? onMute;
  final VoidCallback? onDelete;
  final bool online;

  @override
  Widget build(BuildContext context) {
    final draft = conversation.draftPreview;
    final preview = draft != null && draft.isNotEmpty ? '$draftBadge $draft' : conversation.lastPreview;
    return ListTile(
      key: Key('conversation-${conversation.conversationId}'),
      onTap: onTap,
      leading: Stack(
        children: [
          CircleAvatar(child: Text(conversation.name.isEmpty ? '#' : conversation.name.characters.first)),
          if (online)
            const Positioned(
              right: 0,
              bottom: 0,
              child: Icon(Icons.circle, size: 12, color: Color(0xFF2EAD4B), key: Key('presence-dot')),
            ),
        ],
      ),
      title: Row(
        children: [
          Expanded(child: Text(conversation.name.isEmpty ? conversation.conversationId : conversation.name)),
          if (conversation.pinned) const Icon(Icons.push_pin, size: 16),
          if (conversation.muted) const Icon(Icons.notifications_off, size: 16),
        ],
      ),
      subtitle: Text.rich(TextSpan(children: [
        if (conversation.atPersonal || (conversation.atMe && !conversation.atAll))
          const TextSpan(text: '$mentionMeBadge ', style: TextStyle(color: Colors.red)),
        if (conversation.atAll) const TextSpan(text: '$mentionAllBadge ', style: TextStyle(color: Colors.red)),
        TextSpan(text: preview),
      ])),
      trailing: conversation.unreadCount > 0
          ? CircleAvatar(
              radius: 12,
              child: Text('${conversation.unreadCount}', style: const TextStyle(fontSize: 11)),
            )
          : null,
      onLongPress: () async {
        final action = await showModalBottomSheet<String>(
          context: context,
          builder: (context) => SafeArea(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              ListTile(title: Text(conversation.pinned ? '取消置顶' : '置顶'), onTap: () => Navigator.pop(context, 'pin')),
              ListTile(title: Text(conversation.muted ? '取消免打扰' : '免打扰'), onTap: () => Navigator.pop(context, 'mute')),
              ListTile(title: const Text('删除会话'), onTap: () => Navigator.pop(context, 'delete')),
            ]),
          ),
        );
        if (action == 'pin') onPin?.call();
        if (action == 'mute') onMute?.call();
        if (action == 'delete') onDelete?.call();
      },
    );
  }
}
