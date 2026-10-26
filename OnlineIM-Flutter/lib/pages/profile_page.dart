import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/providers.dart';

class ProfilePage extends ConsumerWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(imSessionProvider);
    final name = session.user?['nickname']?.toString() ?? session.user?['username']?.toString() ?? '未登录';
    final online = session.socket?.state.name == 'online';
    return Scaffold(
      appBar: AppBar(title: const Text('我的')),
      body: ListView(
        children: [
          ListTile(
            leading: const CircleAvatar(child: Icon(Icons.person)),
            title: Text(name, key: const Key('profile-name')),
            subtitle: Text(online ? '在线' : '离线', key: const Key('profile-presence')),
          ),
          SwitchListTile(
            key: const Key('hide-online'),
            title: const Text('隐藏在线状态'),
            value: session.hideOnline,
            onChanged: (value) async {
              session.hideOnline = value;
              session.refresh();
              if (session.loggedIn) await session.api.setHideOnline(value);
            },
          ),
          ListTile(
            key: const Key('devices-entry'),
            leading: const Icon(Icons.devices),
            title: const Text('登录设备'),
            onTap: () async {
              await session.loadDevices();
              if (!context.mounted) return;
              await showModalBottomSheet<void>(
                context: context,
                builder: (context) => _DevicesSheet(session: session),
              );
            },
          ),
          ListTile(
            key: const Key('backup-entry'),
            leading: const Icon(Icons.key),
            title: const Text('密钥备份 / 恢复'),
            subtitle: Text(session.identity == null ? '尚未生成本机密钥' : '指纹 ${session.identity!.fingerprint.substring(0, 8)}'),
            onTap: () => _backup(context, ref),
          ),
          ListTile(
            key: const Key('password-entry'),
            leading: const Icon(Icons.lock_outline),
            title: const Text('修改密码'),
            onTap: () => _password(context, ref),
          ),
          ListTile(
            key: const Key('logout'),
            leading: const Icon(Icons.logout),
            title: const Text('退出登录'),
            onTap: session.logout,
          ),
        ],
      ),
    );
  }

  Future<void> _backup(BuildContext context, WidgetRef ref) async {
    final passphrase = TextEditingController();
    final payload = TextEditingController();
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(key: const Key('backup-passphrase'), controller: passphrase, decoration: const InputDecoration(labelText: '备份口令'), obscureText: true),
          TextField(key: const Key('backup-payload'), controller: payload, decoration: const InputDecoration(labelText: '导入备份 JSON'), maxLines: 3),
          FilledButton(
            key: const Key('backup-export'),
            onPressed: () async {
              final text = await ref.read(imSessionProvider).exportBackup(passphrase.text);
              payload.text = text;
            },
            child: const Text('导出'),
          ),
          TextButton(
            key: const Key('backup-import'),
            onPressed: () async {
              final error = await ref.read(imSessionProvider).importBackup(payload.text, passphrase.text);
              if (context.mounted && error != null) {
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error)));
              }
            },
            child: const Text('导入'),
          ),
        ]),
      ),
    );
    passphrase.dispose();
    payload.dispose();
  }

  Future<void> _password(BuildContext context, WidgetRef ref) async {
    final oldPassword = TextEditingController();
    final newPassword = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('修改密码'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(key: const Key('old-password'), controller: oldPassword, obscureText: true, decoration: const InputDecoration(labelText: '原密码')),
          TextField(key: const Key('new-password'), controller: newPassword, obscureText: true, decoration: const InputDecoration(labelText: '新密码')),
        ]),
        actions: [
          FilledButton(
            key: const Key('password-save'),
            onPressed: () async {
              final error = await ref.read(imSessionProvider).changePassword(oldPassword.text, newPassword.text);
              if (context.mounted) Navigator.pop(context);
              if (error != null && context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error)));
              }
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
    oldPassword.dispose();
    newPassword.dispose();
  }
}

class _DevicesSheet extends StatelessWidget {
  const _DevicesSheet({required this.session});

  final dynamic session;

  @override
  Widget build(BuildContext context) {
    final devices = session.devices as List<Map<String, dynamic>>;
    return ListView(
      children: [
        const ListTile(title: Text('登录设备')),
        for (final device in devices)
          ListTile(
            key: Key('device-${device['device_id']}'),
            title: Text(device['device_name']?.toString() ?? device['device_type']?.toString() ?? '设备'),
            subtitle: Text('${device['online'] == true ? '在线' : '离线'}${device['current'] == true ? ' · 当前设备' : ''}'),
            trailing: device['current'] == true
                ? null
                : TextButton(
                    onPressed: () => session.logoutDevice(device['device_id'].toString()),
                    child: const Text('登出'),
                  ),
          ),
      ],
    );
  }
}
