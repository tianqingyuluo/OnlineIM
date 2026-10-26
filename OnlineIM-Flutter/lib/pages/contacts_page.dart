import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../protocol/conversation_id.dart';
import '../protocol/protocol_engine.dart';
import '../state/providers.dart';

class ContactsPage extends ConsumerStatefulWidget {
  const ContactsPage({super.key});

  @override
  ConsumerState<ContactsPage> createState() => _ContactsPageState();
}

class _ContactsPageState extends ConsumerState<ContactsPage> {
  var _tab = 0;

  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(imSessionProvider).loadContacts());
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(imSessionProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('通讯录'),
        actions: [
          IconButton(key: const Key('create-group'), onPressed: () => _createGroup(context), icon: const Icon(Icons.group_add)),
          IconButton(key: const Key('search-user'), onPressed: () => _searchUser(context), icon: const Icon(Icons.person_search)),
        ],
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 0, label: Text('好友')),
              ButtonSegment(value: 1, label: Text('申请')),
              ButtonSegment(value: 2, label: Text('分组')),
              ButtonSegment(value: 3, label: Text('群聊')),
            ],
            selected: {_tab},
            onSelectionChanged: (value) => setState(() => _tab = value.first),
          ),
        ),
        Expanded(child: _body(session)),
      ]),
    );
  }

  Widget _body(session) {
    switch (_tab) {
      case 1:
        if (session.friendRequests.isEmpty) return const Center(child: Text('没有好友申请'));
        return ListView(
          children: [
            for (final request in session.friendRequests)
              ListTile(
                title: Text(request['sender_info']?['nickname']?.toString() ?? request['request_id']?.toString() ?? '申请'),
                trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                  TextButton(
                    onPressed: () => session.api.handleFriendRequest(request['request_id'].toString(), 'accept').then((_) => session.loadContacts()),
                    child: const Text('同意'),
                  ),
                  TextButton(
                    onPressed: () => session.api.handleFriendRequest(request['request_id'].toString(), 'reject').then((_) => session.loadContacts()),
                    child: const Text('拒绝'),
                  ),
                ]),
              ),
          ],
        );
      case 2:
        if (session.friendGroups.isEmpty) return const Center(child: Text('还没有好友分组'));
        return ListView(
          children: [
            for (final group in session.friendGroups)
              ListTile(title: Text(group['name']?.toString() ?? '分组'), subtitle: Text(group['group_id']?.toString() ?? '')),
          ],
        );
      case 3:
        if (session.groups.isEmpty) return const Center(child: Text('还没有群聊'));
        return ListView(
          children: [
            for (final group in session.groups)
              ListTile(
                title: Text(group['name']?.toString() ?? '群聊'),
                subtitle: Text('${group['member_count'] ?? ''}'),
                onTap: () {
                  final id = group['group_id']?.toString() ?? '';
                  session.engine.conversations[id] = ConversationRecord(conversationId: id, type: 'group', name: group['name']?.toString() ?? '群聊');
                  session.openConversation(id);
                },
              ),
          ],
        );
      default:
        if (session.friends.isEmpty) return const Center(child: Text('还没有好友'));
        return ListView(
          children: [
            for (final friend in session.friends)
              ListTile(
                key: Key('friend-${friend['user_id'] ?? friend['friend_id']}'),
                title: Text(friend['remark']?.toString().isNotEmpty == true ? friend['remark'].toString() : friend['nickname']?.toString() ?? '好友'),
                subtitle: Text(session.engine.presence[friend['user_id'] ?? friend['friend_id']]?.online == true ? '在线' : '离线'),
                onTap: () {
                  final peer = friend['friend_id']?.toString() ?? friend['user_id']?.toString() ?? '';
                  final id = privateConversationId(session.selfId, peer);
                  session.engine.conversations[id] = ConversationRecord(
                    conversationId: id,
                    type: 'private',
                    name: friend['nickname']?.toString() ?? peer,
                    peerId: peer,
                  );
                  session.openConversation(id);
                },
              ),
          ],
        );
    }
  }

  Future<void> _createGroup(BuildContext context) async {
    final name = TextEditingController();
    final created = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('创建群聊'),
        content: TextField(key: const Key('group-name'), controller: name, decoration: const InputDecoration(labelText: '群名称')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          FilledButton(
            key: const Key('group-create'),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('创建'),
          ),
        ],
      ),
    );
    if (created == true && name.text.trim().isNotEmpty) {
      await ref.read(imSessionProvider).api.createGroup(name: name.text.trim());
      await ref.read(imSessionProvider).loadContacts();
    }
    name.dispose();
  }

  Future<void> _searchUser(BuildContext context) async {
    final keyword = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('搜索用户'),
        content: TextField(key: const Key('user-search'), controller: keyword, decoration: const InputDecoration(labelText: '用户名')),
        actions: [
          FilledButton(
            onPressed: () async {
              final session = ref.read(imSessionProvider);
              final users = await session.api.searchUsers(keyword.text.trim());
              if (!context.mounted) return;
              Navigator.pop(context);
              if (users.isEmpty) return;
              final user = users.first;
              await session.api.sendFriendRequest(user['user_id'].toString());
            },
            child: const Text('添加'),
          ),
        ],
      ),
    );
    keyword.dispose();
  }
}
