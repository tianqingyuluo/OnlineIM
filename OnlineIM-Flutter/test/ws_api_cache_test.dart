import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart' hide ConnectionState;
import 'package:flutter_test/flutter_test.dart';
import 'package:onlineim_flutter/config/server_endpoints.dart';
import 'package:onlineim_flutter/data/local_cache_database.dart';
import 'package:onlineim_flutter/data/secure_storage.dart';
import 'package:onlineim_flutter/protocol/protocol_engine.dart';
import 'package:onlineim_flutter/services/api_service.dart';
import 'package:onlineim_flutter/services/foreground_keepalive_service.dart';
import 'package:onlineim_flutter/services/permission_service.dart';
import 'package:onlineim_flutter/services/ws_service.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

class _Adapter implements HttpClientAdapter {
  _Adapter(this.onFetch);
  final Future<ResponseBody> Function(RequestOptions options) onFetch;
  final requests = <RequestOptions>[];

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) {
    requests.add(options);
    return onFetch(options);
  }
}

class _Socket implements WsTransport {
  final messagesController = StreamController<dynamic>();
  final sent = <String>[];
  int? closedWith;

  @override
  int? get closeCode => closedWith;

  @override
  Stream<dynamic> get messages => messagesController.stream;

  @override
  Future<void> close([int? code, String? reason]) async {
    closedWith = code;
    if (!messagesController.isClosed) await messagesController.close();
  }

  @override
  void send(String data) => sent.add(data);
}

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  test('secure store keeps token and identity out of plain maps', () async {
    final store = SecureSessionStore(MemorySecureStore());
    await store.writeToken('jwt-1');
    await store.writeEndpoints(const ServerEndpoints(httpBase: 'http://10.0.2.2:8080/api/v1', wsUrl: 'ws://10.0.2.2:8081/api/v1/chat'));
    expect(await store.readToken(), 'jwt-1');
    expect((await store.readEndpoints()).wsUrl, contains('/api/v1/chat'));
    final uri = ServerEndpoints.defaults.socketUri(token: 'abc def', deviceId: 'phone-1');
    expect(uri.toString(), contains('token=Bearer%20abc%20def'));
    expect(uri.query, contains('device_type=phone'));
    await store.clearSession();
    expect(await store.readToken(), isNull);
    expect(await store.deviceId(), isNotEmpty);
  });

  test('sqlite cache restores conversations, messages, drafts and fingerprints', () async {
    final cache = await LocalCacheDatabase.open(path: inMemoryDatabasePath);
    const user = 'usr_me';
    final conversation = ConversationRecord(
      conversationId: 'conv_1',
      name: '小明',
      draftPreview: '草稿',
      atMe: true,
      pinned: true,
      latestSeq: '12',
    );
    await cache.saveConversation(user, conversation);
    await cache.saveMessage(
      user,
      MessageRecord(messageId: 'msg_2', conversationId: 'conv_1', senderId: 'usr_me', messageType: 'text', content: '后', seqId: '12'),
    );
    await cache.saveMessage(
      user,
      MessageRecord(messageId: 'msg_1', conversationId: 'conv_1', senderId: 'usr_peer', messageType: 'text', content: '先', seqId: '2'),
    );
    await cache.saveDraft(user, DraftRecord(conversationId: 'conv_1', content: '写了一半', updatedAt: 9));
    await cache.saveProfile(user, 'friend', 'usr_peer', {'nickname': '小明'});
    await cache.saveVerifiedFingerprint(user, 'usr_peer', 'abc');
    await cache.saveSyncCursor(user, 'conv_1', '12');

    final messages = await cache.loadMessages(user, 'conv_1');
    expect(messages.map((item) => item.messageId).toList(), ['msg_1', 'msg_2']);
    expect((await cache.loadConversations(user)).single.pinned, isTrue);
    expect((await cache.loadDraft(user, 'conv_1'))?.content, '写了一半');
    expect((await cache.loadProfiles(user, 'friend')).single['nickname'], '小明');
    expect(await cache.loadVerifiedFingerprint(user, 'usr_peer'), 'abc');
    expect(await cache.loadSyncCursor(user, 'conv_1'), '12');
    await cache.close();
  });

  test('login sends phone device fields and bearer interceptor', () async {
    final adapter = _Adapter((options) async {
      if (options.path == '/auth/login') {
        return ResponseBody.fromString(
          jsonEncode({
            'access_token': 'jwt',
            'device_id': 'phone-1',
            'device_type': 'phone',
            'user_info': {'user_id': 'usr_me', 'username': 'me', 'nickname': '我'},
          }),
          200,
          headers: {Headers.contentTypeHeader: ['application/json']},
        );
      }
      return ResponseBody.fromString('{}', 200, headers: {Headers.contentTypeHeader: ['application/json']});
    });
    final dio = Dio(BaseOptions(baseUrl: 'http://10.0.2.2:8080/api/v1'))..httpClientAdapter = adapter;
    final api = ApiService(dio: dio, tokenProvider: () => 'jwt');
    final session = await api.login(username: 'me', password: 'secret1', deviceId: 'phone-1', deviceName: 'Flutter Android');
    expect(session.accessToken, 'jwt');
    final raw = adapter.requests.single.data;
    final body = raw is String ? jsonDecode(raw) as Map : Map<String, dynamic>.from(raw as Map);
    expect(body['device_type'], 'phone');
    expect(body['device_name'], 'Flutter Android');
    await api.devices();
    expect(adapter.requests.last.headers['Authorization'], 'Bearer jwt');
    await api.syncMessages('conv_1', '4');
    expect(adapter.requests.last.path, '/messages/sync/conv_1');
    expect(adapter.requests.last.queryParameters['seq_id'], '4');
    await api.logoutDevice('phone-old');
    expect(adapter.requests.last.path, '/devices/phone-old/logout');
  });

  test('websocket heartbeats and stops reconnecting after SESSION_KICKED', () async {
    final sockets = <_Socket>[];
    final service = WsService(
      endpoints: ServerEndpoints.defaults,
      token: 'jwt',
      deviceId: 'phone-1',
      heartbeatEvery: const Duration(milliseconds: 80),
      delayForAttempt: (_) => 1000,
      connector: (uri) async {
        expect(uri.toString(), contains('device_type=phone'));
        expect(uri.toString(), contains('token=Bearer%20jwt'));
        final socket = _Socket();
        sockets.add(socket);
        return socket;
      },
    );
    await service.connect();
    expect(sockets.single.sent.single, contains('"type":"HEARTBEAT"'));
    await Future<void>.delayed(const Duration(milliseconds: 100));
    expect(sockets.single.sent.length, greaterThanOrEqualTo(2));
    sockets.single.messagesController.add(jsonEncode({'type': 'PONG', 'message': {'serverTime': 1}}));
    await Future<void>.delayed(const Duration(milliseconds: 20));
    expect(service.missedAckCount, 0);
    sockets.single.messagesController.add(jsonEncode({
      'type': 'SESSION_KICKED',
      'message': {'reason': 'REMOTE_LOGOUT', 'message': '该设备已被远程登出'},
    }));
    await Future<void>.delayed(const Duration(milliseconds: 30));
    expect(service.sessionKicked, isTrue);
    await Future<void>.delayed(const Duration(milliseconds: 40));
    expect(sockets, hasLength(1));
    await service.dispose();
  });

  test('a failed socket reconnects when the session is still valid', () async {
    var opens = 0;
    final service = WsService(
      endpoints: ServerEndpoints.defaults,
      token: 'jwt',
      deviceId: 'phone-1',
      heartbeatEvery: const Duration(hours: 1),
      delayForAttempt: (_) => 30,
      sessionProbe: () async => true,
      connector: (uri) async {
        opens += 1;
        if (opens == 1) throw StateError('offline');
        return _Socket();
      },
    );
    await service.connect();
    await Future<void>.delayed(const Duration(milliseconds: 80));
    expect(service.state, ConnectionState.online);
    expect(opens, greaterThanOrEqualTo(2));
    await service.dispose();
  });

  test('foreground service starts in background and permissions are requested for calls', () async {
    final bridge = RecordingKeepalive();
    final service = ForegroundKeepaliveService(bridge: bridge);
    await service.onLifecycle(AppLifecycleState.paused, loggedIn: true);
    expect(bridge.running, isTrue);
    await service.onLifecycle(AppLifecycleState.resumed, loggedIn: false);
    expect(service.running, isFalse);
    expect(bridge.running, isFalse);
    final permissions = PermissionService(gateway: GrantingPermissions());
    expect(await permissions.ensureCall(video: true), isTrue);
    expect(await permissions.ensureNotifications(), isTrue);
    expect(await permissions.ensureMedia(), isTrue);
  });
}
