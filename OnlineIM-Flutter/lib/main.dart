import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'data/local_cache_database.dart';
import 'data/secure_storage.dart';
import 'services/api_service.dart';
import 'services/foreground_keepalive_service.dart';
import 'services/notification_service.dart';
import 'state/im_session.dart';
import 'state/providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  FlutterForegroundTask.initCommunicationPort();
  final secure = SecureSessionStore(KeystoreSecureStore());
  final endpoints = await secure.readEndpoints();
  final cache = await LocalCacheDatabase.open();
  final session = ImSession(
    secure: secure,
    cache: cache,
    api: ApiService(baseUrl: endpoints.normalizedHttp),
    notifications: LocalNotificationService(),
    keepalive: ForegroundKeepaliveService(),
  );
  await session.bootstrap();
  runApp(ProviderScope(
    overrides: [imSessionProvider.overrideWith((ref) => session)],
    child: const OnlineImApp(),
  ));
}
