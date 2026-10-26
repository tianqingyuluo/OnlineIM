import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:onlineim_flutter/app.dart';
import 'package:onlineim_flutter/crypto/e2ee_crypto.dart';
import 'package:onlineim_flutter/data/secure_storage.dart';
import 'package:onlineim_flutter/protocol/protocol_engine.dart';
import 'package:onlineim_flutter/services/api_service.dart';
import 'package:onlineim_flutter/services/call_controller.dart';
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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<ImSession> session({bool loggedIn = false}) async {
    final secure = SecureSessionStore(MemorySecureStore());
    final dio = Dio()..httpClientAdapter = _EmptyAdapter();
    final api = ApiService(dio: dio, baseUrl: 'http://10.0.2.2:8080/api/v1');
    final value = ImSession(secure: secure, api: api, cache: null);
    value.bootstrapped = true;
    if (loggedIn) {
      value.token = 'jwt';
      value.user = {'user_id': 'usr_me', 'username': 'me', 'nickname': '我'};
      value.engine = ProtocolEngine(selfUserId: 'usr_me');
    }
    return value;
  }

  Future<void> pump(WidgetTester tester, ImSession value) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [imSessionProvider.overrideWith((ref) => value)],
      child: const OnlineImApp(),
    ));
    await tester.pumpAndSettle();
  }

  testWidgets('login page edits and persists the server address', (tester) async {
    final value = await session();
    await pump(tester, value);
    expect(find.text('登录'), findsWidgets);
    await tester.tap(find.byKey(const Key('server-settings')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('server-http')), 'http://192.168.1.8:8080/api/v1');
    await tester.enterText(find.byKey(const Key('server-ws')), 'ws://192.168.1.8:8081/api/v1/chat');
    await tester.tap(find.byKey(const Key('server-save')));
    await tester.pumpAndSettle();
    expect(value.endpoints.httpBase, 'http://192.168.1.8:8080/api/v1');
    expect(find.textContaining('192.168.1.8'), findsOneWidget);
    await tester.tap(find.byKey(const Key('register-toggle')));
    await tester.pump();
    expect(find.byKey(const Key('register-nickname')), findsOneWidget);
  });

  testWidgets('conversation list shows draft, mention and presence', (tester) async {
    final value = await session(loggedIn: true);
    value.engine.conversations['conv_usr_meusr_b'] = ConversationRecord(
      conversationId: 'conv_usr_meusr_b',
      type: 'private',
      name: '小明',
      peerId: 'usr_b',
      lastPreview: '你好',
      draftPreview: '写了一半',
      atMe: true,
      atPersonal: true,
      atAll: true,
      unreadCount: 2,
      pinned: true,
      updatedAt: 10,
    );
    value.engine.presence['usr_b'] = PresenceRecord(userId: 'usr_b', status: 'online');
    await pump(tester, value);
    expect(find.textContaining('[草稿]'), findsOneWidget);
    expect(find.textContaining('[有人@我]'), findsOneWidget);
    expect(find.textContaining('[@所有人]'), findsOneWidget);
    expect(find.byKey(const Key('presence-dot')), findsOneWidget);
    await tester.tap(find.byKey(const Key('nav-contacts')));
    await tester.pumpAndSettle();
    expect(find.text('还没有好友'), findsOneWidget);
    await tester.tap(find.byKey(const Key('nav-me')));
    await tester.pumpAndSettle();
    expect(find.text('我'), findsOneWidget);
    expect(find.byKey(const Key('logout')), findsOneWidget);
  });

  testWidgets('chat renders edited, locked, recalled and announcement states', (tester) async {
    final value = await session(loggedIn: true);
    value.engine.conversations['grp_1'] = ConversationRecord(
      conversationId: 'grp_1',
      type: 'group',
      name: '周末小组',
      myRole: 'admin',
    );
    value.engine.messages['msg_1'] = MessageRecord(
      messageId: 'msg_1',
      conversationId: 'grp_1',
      senderId: 'usr_me',
      messageType: 'text',
      content: '改过的正文',
      seqId: '1',
      edited: true,
      timestampMs: DateTime.now().millisecondsSinceEpoch,
    );
    value.engine.messages['msg_2'] = MessageRecord(
      messageId: 'msg_2',
      conversationId: 'grp_1',
      senderId: 'usr_peer',
      messageType: 'text',
      content: '{"ciphertext":"aa","iv":"bb"}',
      seqId: '2',
      isEncrypted: true,
    );
    value.engine.messages['msg_3'] = MessageRecord(
      messageId: 'msg_3',
      conversationId: 'grp_1',
      senderId: 'usr_me',
      messageType: 'text',
      content: '',
      seqId: '3',
      isRecalled: true,
      recalledPlaintext: '原文',
    );
    value.engine.announcements['grp_1:ann_1'] = AnnouncementRecord(
      groupId: 'grp_1',
      announcementId: 'ann_1',
      title: '开会',
      content: '三点',
    );
    await pump(tester, value);
    await tester.tap(find.byKey(const Key('conversation-grp_1')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('edited-badge')), findsOneWidget);
    expect(find.byKey(const Key('e2ee-locked')), findsOneWidget);
    expect(find.textContaining(lockedMessageText), findsOneWidget);
    expect(find.textContaining('重新编辑'), findsOneWidget);
    expect(find.byKey(const Key('announcement-banner')), findsOneWidget);
    await tester.enterText(find.byKey(const Key('chat-input')), '新消息');
    await tester.tap(find.byKey(const Key('chat-send')));
    await tester.pumpAndSettle();
    expect(value.engine.messages.values.any((message) => message.localPlaintext == null && message.content == '新消息'), isTrue);
    await tester.tap(find.byKey(const Key('emoji-button')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('emoji-panel')), findsOneWidget);
  });

  testWidgets('call screen exposes mute, camera, speaker and hangup', (tester) async {
    final value = await session(loggedIn: true);
    value.calls.invite(callId: 'call_1', targetId: 'usr_b', conversationId: 'conv', callType: 'video', sdp: 'offer');
    value.calls.applySignal({'type': 'CALL_ANSWER', 'message': {'call_id': 'call_1', 'sdp': 'answer'}});
    await pump(tester, value);
    await tester.tap(find.byKey(const Key('nav-conversations')));
    await tester.pump();
    expect(find.byKey(const Key('floating-call')), findsNothing);
    value.calls.minimize();
    value.refresh();
    await tester.pump();
    expect(find.byKey(const Key('floating-call')), findsOneWidget);
    await tester.tap(find.byKey(const Key('floating-call')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('call-mute')), findsOneWidget);
    expect(find.byKey(const Key('call-camera')), findsOneWidget);
    expect(find.byKey(const Key('call-speaker')), findsOneWidget);
    await tester.tap(find.byKey(const Key('call-mute')));
    await tester.pump();
    expect(value.calls.state.muted, isTrue);
    await tester.tap(find.byKey(const Key('call-hangup')));
    await tester.pumpAndSettle();
    expect(value.calls.state.phase, CallPhase.ended);
  });
}
