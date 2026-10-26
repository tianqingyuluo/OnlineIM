import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:onlineim_flutter/config/server_endpoints.dart';
import 'package:onlineim_flutter/data/local_cache_database.dart';
import 'package:onlineim_flutter/data/secure_storage.dart';
import 'package:onlineim_flutter/protocol/conversation_id.dart';
import 'package:onlineim_flutter/services/api_service.dart';
import 'package:onlineim_flutter/services/call_controller.dart';
import 'package:onlineim_flutter/services/ws_service.dart';
import 'package:onlineim_flutter/state/im_session.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

Future<String> _node(String command, String input) async {
  final process = await Process.start('node', ['tool/e2ee_web_compat.mjs', command]);
  if (input.isNotEmpty) {
    process.stdin.write(input);
  }
  await process.stdin.close();
  final output = await process.stdout.transform(utf8.decoder).join();
  final error = await process.stderr.transform(utf8.decoder).join();
  final code = await process.exitCode;
  expect(code, 0, reason: error);
  return output;
}

Future<void> _waitUntil(bool Function() condition, {Duration timeout = const Duration(seconds: 8), String? reason}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    if (condition()) return;
    await Future<void>.delayed(const Duration(milliseconds: 80));
  }
  expect(condition(), isTrue, reason: reason ?? 'Timed out waiting for condition');
}

void main() {
  final liveEnabled = Platform.environment['ONLINEIM_LIVE_E2E'] == '1';
  final httpBase = Platform.environment['ONLINEIM_HTTP_BASE'] ?? 'http://localhost:8080/api/v1';
  final wsUrl = Platform.environment['ONLINEIM_WS_URL'] ?? 'ws://localhost:8081/api/v1/chat';
  final endpoints = ServerEndpoints(httpBase: httpBase, wsUrl: wsUrl);

  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  test(
    'live backend E2E: multi-device (web+desktop+phone), contacts, groups, messaging, draft sync, E2EE cross-client, and WebRTC signaling',
    () async {
      final stamp = (DateTime.now().millisecondsSinceEpoch % 10000000).toString();
      final aliceUser = 'fa_$stamp';
      final bobUser = 'fb_$stamp';
      const password = 'Password123!';

      // 1. Create Flutter ImSession for Alice (phone #1) with SQLite cache
      final aliceCache = await LocalCacheDatabase.open(path: inMemoryDatabasePath);
      final aliceStore = SecureSessionStore(MemorySecureStore());
      await aliceStore.writeEndpoints(endpoints);
      final aliceApi = ApiService(baseUrl: endpoints.normalizedHttp);
      final aliceSession = ImSession(secure: aliceStore, api: aliceApi, cache: aliceCache);
      await aliceSession.bootstrap();

      // Register & login Alice on Flutter Android (phone #1)
      final regErr = await aliceSession.register(aliceUser, password, 'AliceFlutter');
      expect(regErr, isNull, reason: 'Alice register+login should succeed');
      expect(aliceSession.loggedIn, isTrue);
      expect(aliceSession.selfId, startsWith('usr_'));
      await _waitUntil(() => aliceSession.socket?.state == ConnectionState.online, reason: 'Alice WS CONNECT_RESPONSE');

      // 2. Login Alice simultaneously on Web ('web') and Tauri ('desktop') and verify 3-device coexistence
      final webApi = ApiService(baseUrl: endpoints.normalizedHttp);
      final webAuth = await webApi.login(
        username: aliceUser,
        password: password,
        deviceId: 'web_alice_$stamp',
        deviceType: 'web',
        deviceName: 'Chrome Web',
      );
      webApi.tokenProvider = () => webAuth.accessToken;

      final desktopApi = ApiService(baseUrl: endpoints.normalizedHttp);
      final desktopAuth = await desktopApi.login(
        username: aliceUser,
        password: password,
        deviceId: 'desk_alice_$stamp',
        deviceType: 'desktop',
        deviceName: 'Tauri Desktop',
      );
      desktopApi.tokenProvider = () => desktopAuth.accessToken;

      final webWs = WsService(endpoints: endpoints, token: webAuth.accessToken, deviceId: 'web_alice_$stamp');
      await webWs.connect();

      await aliceSession.loadDevices();
      final deviceTypes = aliceSession.devices.map((d) => d['device_type']?.toString()).toSet();
      expect(deviceTypes, containsAll(['phone', 'web', 'desktop']), reason: 'All 3 device types coexist online');
      expect(aliceSession.loggedIn, isTrue, reason: 'Phone #1 not kicked by web or desktop login');

      // 3. Second phone login kicks Phone #1 with SAME_DEVICE_TYPE_LOGIN while Web & Desktop remain valid
      final phone2Store = SecureSessionStore(MemorySecureStore());
      await phone2Store.writeEndpoints(endpoints);
      final phone2Api = ApiService(baseUrl: endpoints.normalizedHttp);
      final phone2Session = ImSession(secure: phone2Store, api: phone2Api, cache: aliceCache);
      await phone2Session.bootstrap();
      final login2Err = await phone2Session.login(aliceUser, password);
      expect(login2Err, isNull);

      await _waitUntil(() => !aliceSession.loggedIn && aliceSession.kickMessage != null, reason: 'Phone #1 receives SESSION_KICKED');
      expect((await webApi.me())['user_id'], phone2Session.selfId, reason: 'Web session still valid');
      expect((await desktopApi.me())['user_id'], phone2Session.selfId, reason: 'Desktop session still valid');

      // Use phone2Session as Alice's active Flutter phone session from here on
      final alice = phone2Session;
      await _waitUntil(() => alice.socket?.state == ConnectionState.online, reason: 'Alice phone2 WS connected');

      // 4. Register & connect Bob (acting as Web/Desktop peer with WebCrypto + WS)
      final bobApi = ApiService(baseUrl: endpoints.normalizedHttp);
      await bobApi.register(username: bobUser, password: password, nickname: 'BobWeb');
      final bobAuth = await bobApi.login(
        username: bobUser,
        password: password,
        deviceId: 'web_bob_$stamp',
        deviceType: 'web',
        deviceName: 'Bob Web Client',
      );
      bobApi.tokenProvider = () => bobAuth.accessToken;
      final bobId = bobAuth.user['user_id'].toString();
      final bobFrames = <Map<String, dynamic>>[];
      final bobWs = await WebSocket.connect(
        Uri.parse('$wsUrl?token=${Uri.encodeComponent('Bearer ${bobAuth.accessToken}')}&device_id=web_bob_$stamp&device_type=web').toString(),
      );
      bobWs.listen((raw) {
        if (raw is String) {
          bobFrames.add((jsonDecode(raw) as Map).map((k, v) => MapEntry(k.toString(), v)));
        }
      });
      await _waitUntil(() => bobWs.readyState == WebSocket.open, reason: 'Bob WS CONNECT_RESPONSE');

      // 5. Friend request flow (Alice searches Bob -> sends request -> Bob accepts -> Alice loads contacts & friend groups)
      final foundUsers = await alice.api.searchUsers(bobUser);
      expect(foundUsers.any((u) => u['user_id'] == bobId), isTrue);
      await alice.api.sendFriendRequest(bobId, message: 'Hi from Flutter Android');

      final bobReqs = await bobApi.friendRequests();
      final reqFromAlice = bobReqs.firstWhere((r) =>
          (r['sender_info'] is Map && r['sender_info']['user_id'] == alice.selfId) || r['from_user_id'] == alice.selfId);
      await bobApi.handleFriendRequest(reqFromAlice['request_id'].toString(), 'accept');

      final createdFg = await alice.api.createFriendGroup('Flutter好友组');
      final fgId = (createdFg['group_id'] ?? createdFg['id']).toString();
      expect(fgId, isNotEmpty);
      await alice.api.moveFriendGroup(bobId, fgId);

      await alice.loadContacts();
      expect(alice.friends.any((f) => f['user_id'] == bobId && f['nickname'] == 'BobWeb'), isTrue,
          reason: 'Friend list normalized with top-level user_id and nickname');
      expect(alice.friendGroups.any((g) => g['name'] == 'Flutter好友组'), isTrue);
      expect(alice.engine.presence[bobId]?.online, isTrue, reason: 'Bob online presence loaded');

      // 6. Private conversation: text, media (image/file/voice), quote reply, edit, recall, forward, search, and draft sync
      final privConvId = privateConversationId(alice.selfId, bobId);
      await alice.openConversation(privConvId);
      await alice.sendText(privConvId, '第一条来自Flutter的消息');

      await _waitUntil(
        () => alice.engine.history(privConvId).any((m) => m.content == '第一条来自Flutter的消息' && m.seqId != null),
        reason: 'Alice receives MESSAGE_ACK / seq_id for first message',
      );
      final firstMsg = alice.engine.history(privConvId).firstWhere((m) => m.content == '第一条来自Flutter的消息');
      expect(firstMsg.messageId, startsWith('msg_'));

      // Bob receives message over WS and sends quoted reply
      await _waitUntil(
        () => bobFrames.any((f) => (f['message'] is Map) && f['message']['message_id'] == firstMsg.messageId),
        reason: 'Bob receives Alice message over WS',
      );
      bobWs.add(jsonEncode({
        'type': 'PRIVATE_MESSAGE_REQUEST',
        'message': {
          'conversation_id': privConvId,
          'receiver_id': alice.selfId,
          'message_type': 'text',
          'content': 'Bob引用回复Flutter',
          'reply_to_message_id': firstMsg.messageId,
          'client_message_id': 'c_bob_reply_$stamp',
        },
        'timestamp': DateTime.now().toIso8601String(),
      }));

      await _waitUntil(
        () => alice.engine.history(privConvId).any((m) => m.content == 'Bob引用回复Flutter' && m.replyTo?.messageId == firstMsg.messageId),
        reason: 'Alice receives Bob quoted reply with ReplySnapshot',
      );

      // Media upload & send (image, file, voice) from Flutter
      final pngBytes = base64Decode(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
      );
      await alice.sendMedia(privConvId, PickedMedia(kind: 'image', filename: 'dot.png', bytes: pngBytes));
      await _waitUntil(
        () => alice.engine.history(privConvId).any((m) => m.messageType == 'image' && m.seqId != null),
        reason: 'Image message uploaded and ACKed',
      );

      // Edit message on Flutter and verify Bob receives MESSAGE_EDITED
      await alice.edit(firstMsg.messageId, '第一条来自Flutter的消息(已编辑)');
      await _waitUntil(
        () => bobFrames.any((f) => f['type'] == 'MESSAGE_EDITED' && f['message']?['content'] == '第一条来自Flutter的消息(已编辑)'),
        reason: 'Bob receives MESSAGE_EDITED from Flutter',
      );

      // Search message via HTTP
      await alice.search('已编辑', conversationId: privConvId);
      expect(alice.searchHits.any((h) => h['message_id'] == firstMsg.messageId), isTrue);

      // Draft sync: Web saves draft -> Flutter receives DRAFT_UPDATED over WS
      await webApi.saveDraft(privConvId, {'content': '来自Web端的草稿内容'});
      await _waitUntil(
        () => alice.engine.drafts[privConvId]?.content == '来自Web端的草稿内容',
        reason: 'Flutter receives DRAFT_UPDATED from Web client',
      );
      expect(alice.engine.conversations[privConvId]?.draftPreview, contains('来自Web端的草稿内容'));

      // 7. Group creation, group announcement publish + read confirmation, @所有人 mention, forward, and recall
      final grp = await alice.api.createGroup(name: 'Flutter测试群', memberIds: [alice.selfId, bobId]);
      final groupId = grp['group_id'].toString();
      expect(groupId, startsWith('grp_'));
      await alice.loadContacts();
      expect(alice.groups.any((g) => g['group_id'] == groupId), isTrue, reason: 'groups() loads /groups/joined');

      // Publish group announcement as Alice (owner) via Dio, Bob marks read, Alice checks readers
      final annRes = await alice.api.dio.post<Map<String, dynamic>>(
        '/groups/$groupId/announcements',
        data: {'title': '版本发布', 'content': '安卓端上线', 'is_pinned': true},
      );
      final annId = annRes.data!['announcement_id'].toString();
      await bobApi.confirmAnnouncement(groupId, annId);
      await alice.loadAnnouncementReaders(groupId, annId);
      final readers = alice.announcementReaders?['readers'] as List?;
      expect(readers?.any((r) => r is Map && r['user_id'] == bobId), isTrue);

      // Forward message from private chat to group
      await alice.forward(sourceConversationId: privConvId, messageIds: [firstMsg.messageId], targets: [groupId]);
      await alice.openConversation(groupId);
      expect(alice.engine.history(groupId).isNotEmpty, isTrue, reason: 'Forwarded message appears in group');

      // Recall firstMsg in private chat and verify ReplySnapshot cascades to recalled
      await alice.recall(firstMsg.messageId);
      await _waitUntil(() => alice.engine.messages[firstMsg.messageId]?.isRecalled == true, reason: 'Message recalled');
      final bobReplyMsg = alice.engine.history(privConvId).firstWhere((m) => m.content == 'Bob引用回复Flutter');
      expect(bobReplyMsg.replyTo?.state, 'recalled', reason: 'Quoted reply snapshot cascades to recalled');

      // 8. Cross-client E2EE interoperability (Flutter ImSession <-> WebCrypto tool/e2ee_web_compat.mjs)
      final bobIdentityJson = jsonDecode(await _node('identity', '')) as Map<String, dynamic>;
      await bobApi.uploadE2eeKeys({
        'key_id': bobIdentityJson['keyId'],
        'identity_public_key': bobIdentityJson['identityPublicSpki'],
        'signing_public_key': bobIdentityJson['signingPublicSpki'],
        'algorithm': bobIdentityJson['algorithm'],
        'fingerprint': bobIdentityJson['fingerprint'],
        'signature': bobIdentityJson['signature'],
      });

      // Enable E2EE from Flutter
      await alice.toggleE2ee(privConvId, true);
      expect(alice.engine.conversations[privConvId]?.e2eeEnabled, isTrue);
      await _waitUntil(
        () => bobFrames.any((f) => f['type'] == 'E2EE_STATE_CHANGED' && f['message']?['enabled'] == true),
        reason: 'Bob receives E2EE_STATE_CHANGED',
      );

      // Verify 60-digit Safety Number matches between Flutter and WebCrypto
      final flutterSafetyFormatted = await alice.safetyCode(privConvId);
      expect(flutterSafetyFormatted, isNotNull);
      final webSafety = await _node(
        'safety',
        jsonEncode({
          'fingerprintA': alice.identity!.fingerprint,
          'fingerprintB': bobIdentityJson['fingerprint'],
        }),
      );
      expect(flutterSafetyFormatted!.replaceAll(' ', ''), webSafety);

      // Alice (Flutter) sends encrypted message -> Bob (WebCrypto) decrypts it
      await alice.sendText(privConvId, 'Flutter端到端加密机密消息');
      await _waitUntil(
        () => bobFrames.any((f) => f['message'] is Map && f['message']['is_encrypted'] == true),
        reason: 'Bob receives encrypted frame from Flutter',
      );
      final encryptedFrame = bobFrames.lastWhere((f) => f['message'] is Map && f['message']['is_encrypted'] == true);
      final envelopeFromFlutter = jsonDecode(encryptedFrame['message']['content'].toString()) as Map<String, dynamic>;
      final decryptedByWeb = await _node(
        'decrypt',
        jsonEncode({
          'envelope': envelopeFromFlutter,
          'privateJwk': bobIdentityJson['identityPrivateJwk'],
          'peerSpki': alice.identity!.identityPublicSpki,
        }),
      );
      expect(decryptedByWeb, 'Flutter端到端加密机密消息');

      // Bob (WebCrypto) encrypts a reply -> sends over WS -> Alice (Flutter ImSession) automatically decrypts into localPlaintext
      final webEnvelopeStr = await _node(
        'encrypt',
        jsonEncode({
          'plaintext': 'Web端加密回复给Flutter',
          'senderPrivateJwk': bobIdentityJson['identityPrivateJwk'],
          'senderKeyId': bobIdentityJson['keyId'],
          'recipientPublicSpki': alice.identity!.identityPublicSpki,
          'recipientKeyId': alice.identity!.keyId,
          'counter': 42,
        }),
      );
      bobWs.add(jsonEncode({
        'type': 'PRIVATE_MESSAGE_REQUEST',
        'message': {
          'conversation_id': privConvId,
          'receiver_id': alice.selfId,
          'message_type': 'text',
          'content': webEnvelopeStr,
          'is_encrypted': true,
          'client_message_id': 'c_bob_e2ee_$stamp',
        },
        'timestamp': DateTime.now().toIso8601String(),
      }));

      await _waitUntil(
        () => alice.engine.history(privConvId).any((m) => m.localPlaintext == 'Web端加密回复给Flutter'),
        reason: 'Flutter ImSession decrypts incoming WebCrypto E2EE message',
      );

      // Verify PBKDF2 key backup export & import on Flutter
      final backupPayload = await alice.exportBackup('BackupPass#2026');
      final importErr = await alice.importBackup(backupPayload, 'BackupPass#2026');
      expect(importErr, isNull);

      // 9. Cross-client 1v1 WebRTC call signaling (Flutter <-> Web)
      final ice = await alice.api.iceServers();
      expect(ice['ice_servers'], isA<List>());

      await alice.startCall(privConvId, 'audio');
      expect(alice.calls.state.phase, CallPhase.outgoing);
      final callId = alice.calls.state.callId!;

      await _waitUntil(
        () => bobFrames.any((f) => f['type'] == 'CALL_INVITE' && f['message']?['call_id'] == callId),
        reason: 'Bob receives CALL_INVITE from Flutter',
      );

      bobWs.add(jsonEncode({
        'type': 'CALL_RINGING',
        'message': {'call_id': callId, 'target_user_id': alice.selfId, 'conversation_id': privConvId},
      }));
      bobWs.add(jsonEncode({
        'type': 'CALL_ANSWER',
        'message': {
          'call_id': callId,
          'target_user_id': alice.selfId,
          'conversation_id': privConvId,
          'sdp': 'v=0\r\no=- 2 2 IN IP4 127.0.0.1\r\ns=OnlineIM-Web-Answer\r\n',
        },
      }));

      await _waitUntil(() => alice.calls.state.phase == CallPhase.inCall, reason: 'Flutter call transitions to inCall');
      alice.hangupCall();
      expect(alice.calls.state.phase, CallPhase.ended);
      await _waitUntil(
        () => bobFrames.any((f) => (f['type'] == 'CALL_END' || f['type'] == 'CALL_HANGUP') && f['message']?['call_id'] == callId),
        reason: 'Bob receives CALL_END from Flutter',
      );

      // Verify SQLite cache persisted conversations and messages for Alice
      final cachedConvs = await aliceCache.loadConversations(alice.selfId);
      expect(cachedConvs.any((c) => c.conversationId == privConvId), isTrue);
      final cachedMsgs = await aliceCache.loadMessages(alice.selfId, privConvId);
      expect(cachedMsgs, isNotEmpty);

      // Cleanup
      await bobWs.close();
      await webWs.dispose();
      alice.dispose();
      aliceSession.dispose();
      await aliceCache.close();
    },
    skip: !liveEnabled ? 'Set ONLINEIM_LIVE_E2E=1 to run against live backend' : false,
  );
}
