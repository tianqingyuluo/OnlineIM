import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'pages/call_page.dart';
import 'pages/chat_page.dart';
import 'pages/login_page.dart';
import 'pages/main_shell.dart';
import 'state/providers.dart';

class OnlineImApp extends ConsumerStatefulWidget {
  const OnlineImApp({super.key});

  @override
  ConsumerState<OnlineImApp> createState() => _OnlineImAppState();
}

class _OnlineImAppState extends ConsumerState<OnlineImApp> {
  late final GoRouter router = _build();

  GoRouter _build() {
    final session = ref.read(imSessionProvider);
    return GoRouter(
      refreshListenable: session,
      initialLocation: session.loggedIn ? '/' : '/login',
      redirect: (context, state) {
        final loggingIn = state.matchedLocation == '/login';
        if (!session.loggedIn && !loggingIn) return '/login';
        if (session.loggedIn && loggingIn) return '/';
        return null;
      },
      routes: [
        GoRoute(path: '/login', builder: (context, state) => const LoginPage()),
        GoRoute(
          path: '/',
          builder: (context, state) => const MainShell(),
          routes: [
            GoRoute(
              path: 'chat/:id',
              builder: (context, state) => ChatPage(conversationId: state.pathParameters['id']!),
            ),
            GoRoute(path: 'call', builder: (context, state) => const CallPage()),
          ],
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'OnlineIM',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1F8A70), brightness: Brightness.light),
        useMaterial3: true,
        appBarTheme: const AppBarTheme(centerTitle: false),
      ),
      routerConfig: router,
    );
  }
}
