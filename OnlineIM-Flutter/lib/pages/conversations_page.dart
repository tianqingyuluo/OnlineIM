import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../state/providers.dart';
import '../widgets/conversation_tile.dart';

class ConversationsPage extends ConsumerStatefulWidget {
  const ConversationsPage({super.key});

  @override
  ConsumerState<ConversationsPage> createState() => _ConversationsPageState();
}

class _ConversationsPageState extends ConsumerState<ConversationsPage> {
  var _query = '';

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(imSessionProvider);
    final items = session.engine.conversations.values.where((item) {
      if (_query.isEmpty) return true;
      return item.name.contains(_query) || item.lastPreview.contains(_query);
    }).toList()
      ..sort((a, b) {
        if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
        return b.updatedAt.compareTo(a.updatedAt);
      });
    return Scaffold(
      appBar: AppBar(title: const Text('会话')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: TextField(
              key: const Key('conversation-search'),
              decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: '搜索会话或聊天记录'),
              onChanged: (value) {
                setState(() => _query = value.trim());
                if (value.trim().isNotEmpty) session.search(value.trim());
              },
            ),
          ),
          Expanded(
            child: items.isEmpty
                ? const Center(child: Text('暂无会话'))
                : ListView.builder(
                    itemCount: items.length,
                    itemBuilder: (context, index) {
                      final item = items[index];
                      final peer = item.peerId;
                      final online = peer != null && (session.engine.presence[peer]?.online ?? false);
                      return ConversationTile(
                        conversation: item,
                        online: online,
                        onTap: () {
                          session.openConversation(item.conversationId);
                          context.push('/chat/${item.conversationId}');
                        },
                        onPin: () => session.togglePin(item.conversationId),
                        onMute: () => session.toggleMute(item.conversationId),
                        onDelete: () => session.removeConversation(item.conversationId),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
