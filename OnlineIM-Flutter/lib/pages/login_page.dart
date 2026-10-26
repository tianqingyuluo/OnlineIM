import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/server_endpoints.dart';
import '../state/im_session.dart';
import '../state/providers.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _nickname = TextEditingController();
  var _register = false;
  var _busy = false;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    _nickname.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(imSessionProvider);
    final kicked = session.kickMessage;
    return Scaffold(
      appBar: AppBar(
        title: const Text('OnlineIM'),
        actions: [
          IconButton(
            key: const Key('server-settings'),
            onPressed: () => _editServer(context),
            icon: const Icon(Icons.dns_outlined),
            tooltip: '服务器设置',
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          if (kicked != null)
            Card(
              color: Theme.of(context).colorScheme.errorContainer,
              child: ListTile(title: Text(kicked), leading: const Icon(Icons.warning_amber)),
            ),
          Text(_register ? '注册' : '登录', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 8),
          Text('当前服务器 ${session.endpoints.normalizedHttp}', key: const Key('server-label')),
          const SizedBox(height: 16),
          TextField(key: const Key('login-username'), controller: _username, decoration: const InputDecoration(labelText: '用户名')),
          if (_register) TextField(key: const Key('register-nickname'), controller: _nickname, decoration: const InputDecoration(labelText: '昵称')),
          TextField(
            key: const Key('login-password'),
            controller: _password,
            obscureText: true,
            decoration: const InputDecoration(labelText: '密码'),
          ),
          const SizedBox(height: 16),
          FilledButton(
            key: const Key('login-submit'),
            onPressed: _busy
                ? null
                : () async {
                    setState(() => _busy = true);
                    final error = _register
                        ? await session.register(_username.text, _password.text, _nickname.text)
                        : await session.login(_username.text, _password.text);
                    if (!context.mounted) return;
                    setState(() => _busy = false);
                    if (error != null) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error)));
                    }
                  },
            child: Text(_register ? '注册并登录' : '登录'),
          ),
          TextButton(
            key: const Key('register-toggle'),
            onPressed: () => setState(() => _register = !_register),
            child: Text(_register ? '已有账号，去登录' : '没有账号，去注册'),
          ),
        ],
      ),
    );
  }

  Future<void> _editServer(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => _ServerSettingsSheet(session: ref.read(imSessionProvider)),
    );
  }
}

class _ServerSettingsSheet extends StatefulWidget {
  const _ServerSettingsSheet({required this.session});

  final ImSession session;

  @override
  State<_ServerSettingsSheet> createState() => _ServerSettingsSheetState();
}

class _ServerSettingsSheetState extends State<_ServerSettingsSheet> {
  late final TextEditingController _http = TextEditingController(text: widget.session.endpoints.httpBase);
  late final TextEditingController _ws = TextEditingController(text: widget.session.endpoints.wsUrl);

  @override
  void dispose() {
    _http.dispose();
    _ws.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(left: 24, right: 24, top: 24, bottom: 24 + MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('服务器设置'),
            TextField(key: const Key('server-http'), controller: _http, decoration: const InputDecoration(labelText: 'HTTP API')),
            TextField(key: const Key('server-ws'), controller: _ws, decoration: const InputDecoration(labelText: 'WebSocket')),
            const SizedBox(height: 12),
            FilledButton(
              key: const Key('server-save'),
              onPressed: () async {
                await widget.session.saveEndpoints(ServerEndpoints(httpBase: _http.text.trim(), wsUrl: _ws.text.trim()));
                if (context.mounted) Navigator.pop(context);
              },
              child: const Text('保存'),
            ),
          ],
        ),
      ),
    );
  }
}
