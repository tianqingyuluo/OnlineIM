import 'package:flutter/material.dart';

String friendIdOf(Map<String, dynamic> friend) {
  final friendId = friend['friend_id']?.toString() ?? '';
  if (friendId.isNotEmpty) return friendId;
  return friend['user_id']?.toString() ?? '';
}

String friendLabelOf(Map<String, dynamic> friend) {
  final remark = friend['remark']?.toString() ?? '';
  if (remark.isNotEmpty) return remark;
  final nickname = friend['nickname']?.toString() ?? '';
  if (nickname.isNotEmpty) return nickname;
  final username = friend['username']?.toString() ?? '';
  if (username.isNotEmpty) return username;
  return friendIdOf(friend);
}

class FriendCheckList extends StatelessWidget {
  const FriendCheckList({
    super.key,
    required this.friends,
    required this.selected,
    required this.onChanged,
    this.keyPrefix = 'friend-pick',
    this.emptyText = '还没有好友',
  });

  final List<Map<String, dynamic>> friends;
  final Set<String> selected;
  final ValueChanged<Set<String>> onChanged;
  final String keyPrefix;
  final String emptyText;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (final friend in friends) {
      final id = friendIdOf(friend);
      if (id.isEmpty) continue;
      rows.add(CheckboxListTile(
        key: Key('$keyPrefix-$id'),
        value: selected.contains(id),
        title: Text(friendLabelOf(friend)),
        controlAffinity: ListTileControlAffinity.leading,
        contentPadding: EdgeInsets.zero,
        onChanged: (checked) {
          final next = {...selected};
          if (checked == true) {
            next.add(id);
          } else {
            next.remove(id);
          }
          onChanged(next);
        },
      ));
    }
    if (rows.isEmpty) return Center(child: Text(emptyText));
    return ListView(shrinkWrap: true, children: rows);
  }
}

Future<List<String>?> pickFriends(
  BuildContext context, {
  required List<Map<String, dynamic>> friends,
  required String title,
  String confirmLabel = '确定',
  Key confirmKey = const Key('friend-pick-confirm'),
  String keyPrefix = 'friend-pick',
  String emptyText = '还没有好友',
}) {
  return showDialog<List<String>>(
    context: context,
    builder: (context) => _FriendPickDialog(
      friends: friends,
      title: title,
      confirmLabel: confirmLabel,
      confirmKey: confirmKey,
      keyPrefix: keyPrefix,
      emptyText: emptyText,
    ),
  );
}

class _FriendPickDialog extends StatefulWidget {
  const _FriendPickDialog({
    required this.friends,
    required this.title,
    required this.confirmLabel,
    required this.confirmKey,
    required this.keyPrefix,
    required this.emptyText,
  });

  final List<Map<String, dynamic>> friends;
  final String title;
  final String confirmLabel;
  final Key confirmKey;
  final String keyPrefix;
  final String emptyText;

  @override
  State<_FriendPickDialog> createState() => _FriendPickDialogState();
}

class _FriendPickDialogState extends State<_FriendPickDialog> {
  var _selected = <String>{};

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      key: const Key('friend-pick-dialog'),
      title: Text(widget.title),
      content: SizedBox(
        width: double.maxFinite,
        height: 280,
        child: FriendCheckList(
          friends: widget.friends,
          selected: _selected,
          keyPrefix: widget.keyPrefix,
          emptyText: widget.emptyText,
          onChanged: (next) => setState(() => _selected = next),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
        FilledButton(
          key: widget.confirmKey,
          onPressed: _selected.isEmpty ? null : () => Navigator.pop(context, _selected.toList()),
          child: Text(widget.confirmLabel),
        ),
      ],
    );
  }
}
