import 'package:flutter/material.dart';

import '../services/api_service.dart';
import '../state/im_session.dart';
import 'friend_picker.dart';

Future<void> showGroupSettings(
  BuildContext context,
  ImSession session, {
  required String groupId,
  String? name,
}) {
  return showModalBottomSheet<void>(
    context: context,
    builder: (sheetContext) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            key: const Key('group-settings-title'),
            title: Text(name == null || name.isEmpty ? '群设置' : name),
            subtitle: const Text('群设置'),
          ),
          ListTile(
            key: const Key('invite-friends'),
            leading: const Icon(Icons.person_add_alt),
            title: const Text('邀请好友'),
            onTap: () async {
              Navigator.pop(sheetContext);
              if (!context.mounted) return;
              final ids = await pickFriends(
                context,
                friends: session.friends,
                title: '邀请好友入群',
                confirmLabel: '邀请',
                confirmKey: const Key('invite-confirm'),
                keyPrefix: 'invite-friend',
                emptyText: '还没有可邀请的好友',
              );
              if (ids == null || ids.isEmpty || !context.mounted) return;
              try {
                await session.api.inviteToGroup(groupId, ids);
                if (!context.mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('邀请已发送')));
              } on ApiException catch (error) {
                if (!context.mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
              }
            },
          ),
        ],
      ),
    ),
  );
}
