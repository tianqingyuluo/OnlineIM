import 'dart:io';
import 'dart:ui' as ui;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:onlineim_flutter/crypto/e2ee_crypto.dart';
import 'package:onlineim_flutter/data/secure_storage.dart';
import 'package:onlineim_flutter/pages/chat_page.dart';
import 'package:onlineim_flutter/pages/login_page.dart';
import 'package:onlineim_flutter/pages/main_shell.dart';
import 'package:onlineim_flutter/protocol/protocol_engine.dart';
import 'package:onlineim_flutter/services/api_service.dart';
import 'package:onlineim_flutter/state/im_session.dart';
import 'package:onlineim_flutter/state/providers.dart';

class _EmptyAdapter implements HttpClientAdapter {
  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    return ResponseBody.fromString('[]', 200, headers: {Headers.contentTypeHeader: ['application/json']});
  }
}

Future<void> _loadCjk() async {
  const subset = '/tmp/onlineim-cjk-subset.ttf';
  const fallback = '/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf';
  final path = File(subset).existsSync() ? subset : fallback;
  final bytes = await File(path).readAsBytes();
  final loader = FontLoader('DroidSansFallback')..addFont(Future.value(ByteData.sublistView(bytes)));
  await loader.load();
}

Future<ImSession> _session({bool loggedIn = false}) async {
  final secure = SecureSessionStore(MemorySecureStore());
  final dio = Dio()..httpClientAdapter = _EmptyAdapter();
  final api = ApiService(dio: dio, baseUrl: 'http://10.0.2.2:8080/api/v1');
  final value = ImSession(secure: secure, api: api, cache: null);
  value.bootstrapped = true;
  if (!loggedIn) return value;
  value.token = 'jwt';
  value.user = {'user_id': 'usr_me', 'username': 'linxia', 'nickname': '林夏'};
  value.engine = ProtocolEngine(selfUserId: 'usr_me');
  value.identity = StoredIdentity(
    keyId: 'key_a1b2c3d4e5f67890',
    algorithm: e2eeAlgorithm,
    identityPublicJwk: const {},
    identityPrivateJwk: const {},
    signingPublicJwk: const {},
    signingPrivateJwk: const {},
    identityPublicSpki: '',
    signingPublicSpki: '',
    fingerprint: 'a1b2c3d4e5f678901234567890abcdefa1b2c3d4e5f678901234567890abcdef',
  );
  return value;
}

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(390 * 2, 844 * 2);
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Widget _app(ImSession session, Widget home) {
  return ProviderScope(
    overrides: [imSessionProvider.overrideWith((ref) => session)],
    child: MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        fontFamily: 'DroidSansFallback',
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1F8A70), brightness: Brightness.light),
        useMaterial3: true,
        appBarTheme: const AppBarTheme(centerTitle: false),
      ),
      home: home,
    ),
  );
}

Future<void> _shoot(WidgetTester tester, String name) async {
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pump();
  RenderRepaintBoundary? best;
  var area = 0.0;
  for (final element in find.byType(RepaintBoundary).evaluate()) {
    final object = element.renderObject;
    if (object is! RenderRepaintBoundary) continue;
    final next = object.size.width * object.size.height;
    if (next > area) {
      area = next;
      best = object;
    }
  }
  expect(best, isNotNull, reason: '没有可截图的界面');
  final boundary = best!;
  final file = File('/tmp/onlineim-shots/$name.png');
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 2);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    await file.parent.create(recursive: true);
    await file.writeAsBytes(data!.buffer.asUint8List());
  });
  expect(file.existsSync() && file.lengthSync() > 8000, isTrue);
  await tester.pump(const Duration(milliseconds: 1));
  await tester.pump(const Duration(milliseconds: 1));
}

void main() {
  final enabled = Platform.environment['ONLINEIM_UI_SHOTS'] == '1';

  setUpAll(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    if (!enabled) return;
    await _loadCjk();
  });

  testWidgets('login page', (tester) async {
    if (!enabled) return;
    _phone(tester);
    final session = await _session();
    await tester.pumpWidget(_app(session, const LoginPage()));
    await tester.pump();
    await tester.enterText(find.byKey(const Key('login-username')), 'linxia');
    await tester.enterText(find.byKey(const Key('login-password')), 'secret');
    await tester.pump();
    await _shoot(tester, 'flutter-login');
  }, skip: !enabled);

  testWidgets('conversation list', (tester) async {
    if (!enabled) return;
    _phone(tester);
    final session = await _session(loggedIn: true);
    session.engine.conversations['conv_usr_meusr_b'] = ConversationRecord(
      conversationId: 'conv_usr_meusr_b',
      type: 'private',
      name: '小明',
      peerId: 'usr_b',
      lastPreview: '今晚七点老地方见',
      draftPreview: '我先出发',
      atMe: true,
      atPersonal: true,
      unreadCount: 2,
      pinned: true,
      updatedAt: 30,
    );
    session.engine.conversations['grp_weekend'] = ConversationRecord(
      conversationId: 'grp_weekend',
      type: 'group',
      name: '周末出游',
      lastPreview: '小红: 门票我买好了',
      atAll: true,
      unreadCount: 5,
      updatedAt: 20,
    );
    session.engine.conversations['conv_usr_meusr_c'] = ConversationRecord(
      conversationId: 'conv_usr_meusr_c',
      type: 'private',
      name: '小红',
      peerId: 'usr_c',
      lastPreview: '照片发你了',
      updatedAt: 10,
    );
    session.engine.presence['usr_b'] = PresenceRecord(userId: 'usr_b', status: 'online');
    await tester.pumpWidget(_app(session, const MainShell()));
    await tester.pump();
    await _shoot(tester, 'flutter-conversations');
  }, skip: !enabled);

  testWidgets('private chat', (tester) async {
    if (!enabled) return;
    _phone(tester);
    final session = await _session(loggedIn: true);
    session.engine.conversations['conv_usr_meusr_b'] = ConversationRecord(
      conversationId: 'conv_usr_meusr_b',
      type: 'private',
      name: '小明',
      peerId: 'usr_b',
      e2eeEnabled: true,
    );
    session.engine.presence['usr_b'] = PresenceRecord(userId: 'usr_b', status: 'online');
    session.engine.messages['msg_1'] = MessageRecord(
      messageId: 'msg_1',
      conversationId: 'conv_usr_meusr_b',
      senderId: 'usr_b',
      senderName: '小明',
      messageType: 'text',
      content: '到了跟我说一声',
      seqId: '1',
      deliveryState: 'read',
      timestampMs: DateTime.now().millisecondsSinceEpoch,
    );
    session.engine.messages['msg_2'] = MessageRecord(
      messageId: 'msg_2',
      conversationId: 'conv_usr_meusr_b',
      senderId: 'usr_me',
      messageType: 'text',
      content: '好，我带上门禁卡',
      seqId: '2',
      deliveryState: 'read',
      timestampMs: DateTime.now().millisecondsSinceEpoch,
    );
    session.engine.messages['msg_3'] = MessageRecord(
      messageId: 'msg_3',
      conversationId: 'conv_usr_meusr_b',
      senderId: 'usr_b',
      messageType: 'text',
      content: '{"ciphertext":"aa","iv":"bb"}',
      seqId: '3',
      isEncrypted: true,
      timestampMs: DateTime.now().millisecondsSinceEpoch,
    );
    await tester.pumpWidget(_app(session, const ChatPage(conversationId: 'conv_usr_meusr_b')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await _shoot(tester, 'flutter-private-chat');
  }, skip: !enabled);

  testWidgets('group chat', (tester) async {
    if (!enabled) return;
    _phone(tester);
    final session = await _session(loggedIn: true);
    session.engine.conversations['grp_weekend'] = ConversationRecord(
      conversationId: 'grp_weekend',
      type: 'group',
      name: '周末出游',
      myRole: 'owner',
    );
    session.engine.messages['msg_g1'] = MessageRecord(
      messageId: 'msg_g1',
      conversationId: 'grp_weekend',
      senderId: 'usr_b',
      senderName: '小明',
      messageType: 'text',
      content: '周六上午九点南门集合',
      seqId: '1',
      timestampMs: DateTime.now().millisecondsSinceEpoch,
    );
    session.engine.messages['msg_g2'] = MessageRecord(
      messageId: 'msg_g2',
      conversationId: 'grp_weekend',
      senderId: 'usr_me',
      messageType: 'text',
      content: '@所有人 记得带雨衣',
      seqId: '2',
      deliveryState: 'delivered',
      timestampMs: DateTime.now().millisecondsSinceEpoch,
    );
    session.engine.announcements['grp_weekend:ann_1'] = AnnouncementRecord(
      groupId: 'grp_weekend',
      announcementId: 'ann_1',
      title: '集合公告',
      content: '下雨改到地铁站',
    );
    await tester.pumpWidget(_app(session, const ChatPage(conversationId: 'grp_weekend')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await _shoot(tester, 'flutter-group-chat');
  }, skip: !enabled);

  testWidgets('contacts create group dialog', (tester) async {
    if (!enabled) return;
    _phone(tester);
    final session = await _session(loggedIn: true);
    await tester.pumpWidget(_app(session, const MainShell()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    session.friends = [
      {'friend_id': 'usr_b', 'user_id': 'usr_b', 'nickname': '小明'},
      {'friend_id': 'usr_c', 'user_id': 'usr_c', 'nickname': '小红'},
      {'friend_id': 'usr_d', 'user_id': 'usr_d', 'nickname': '阿凯'},
    ];
    session.groups = [
      {'group_id': 'grp_weekend', 'name': '周末出游', 'member_count': 4},
    ];
    session.refresh();
    await tester.tap(find.byKey(const Key('nav-contacts')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('create-group')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.enterText(find.byKey(const Key('group-name')), '周末出游');
    await tester.tap(find.byKey(const Key('group-member-usr_b')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('group-member-usr_c')));
    await tester.pump();
    await _shoot(tester, 'flutter-contacts-create-group');
  }, skip: !enabled);

  testWidgets('settings and e2ee', (tester) async {
    if (!enabled) return;
    _phone(tester);
    final session = await _session(loggedIn: true);
    await tester.pumpWidget(_app(session, const MainShell()));
    await tester.pump();
    await tester.tap(find.byKey(const Key('nav-me')));
    await tester.pump();
    expect(find.textContaining('指纹'), findsOneWidget);
    await _shoot(tester, 'flutter-settings-e2ee');
  }, skip: !enabled);
}
