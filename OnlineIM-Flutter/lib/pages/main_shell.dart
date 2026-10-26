import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../state/providers.dart';
import '../widgets/call_floating_bar.dart';
import 'contacts_page.dart';
import 'conversations_page.dart';
import 'profile_page.dart';

class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key});

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> with WidgetsBindingObserver {
  var _index = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    ref.read(imSessionProvider).onLifecycle(state);
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(imSessionProvider);
    return Scaffold(
      body: Stack(
        children: [
          IndexedStack(
            index: _index,
            children: const [
              ConversationsPage(),
              ContactsPage(),
              ProfilePage(),
            ],
          ),
          Positioned(
            left: 16,
            bottom: 16,
            child: CallFloatingBar(
              state: session.calls.state,
              onRestore: () => context.push('/call'),
              onHangup: session.hangupCall,
            ),
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(key: Key('nav-conversations'), icon: Icon(Icons.chat_bubble_outline), label: '会话'),
          NavigationDestination(key: Key('nav-contacts'), icon: Icon(Icons.contacts_outlined), label: '通讯录'),
          NavigationDestination(key: Key('nav-me'), icon: Icon(Icons.person_outline), label: '我的'),
        ],
      ),
    );
  }
}
