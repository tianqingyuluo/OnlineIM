import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:uuid/uuid.dart';

import '../config/server_endpoints.dart';
import '../crypto/e2ee_crypto.dart';
import '../data/local_cache_database.dart';
import '../data/secure_storage.dart';
import '../protocol/conversation_id.dart';
import '../protocol/frame_types.dart';
import '../protocol/preview_text.dart';
import '../protocol/protocol_engine.dart';
import '../services/api_service.dart';
import '../services/call_controller.dart';
import '../services/draft_sync.dart';
import '../services/foreground_keepalive_service.dart';
import '../services/notification_service.dart';
import '../services/permission_service.dart';
import '../services/ws_service.dart';

class PickedMedia {
  PickedMedia({required this.kind, required this.filename, required this.bytes, this.durationMs});

  final String kind;
  final String filename;
  final List<int> bytes;
  final int? durationMs;
}

abstract class MediaPicker {
  Future<PickedMedia?> pickImage();
  Future<PickedMedia?> pickFile();
  Future<PickedMedia?> recordVoice();
}

class ImSession extends ChangeNotifier {
  ImSession({
    required this.secure,
    required this.api,
    required this.cache,
    WsService? socket,
    NotificationSink? notifications,
    PermissionService? permissions,
    ForegroundKeepaliveService? keepalive,
    CallMedia? media,
    this.picker,
    DateTime Function()? now,
  })  : notifications = notifications ?? RecordingNotifications(),
        permissions = permissions ?? PermissionService(gateway: GrantingPermissions()),
        keepalive = keepalive ?? ForegroundKeepaliveService(bridge: RecordingKeepalive()),
        media = media ?? MemoryCallMedia(),
        now = now ?? DateTime.now {
    api.tokenProvider ??= () => token;
    calls = CallController();
    engine = ProtocolEngine(selfUserId: '');
  }

  final SecureSessionStore secure;
  final ApiService api;
  final LocalCacheDatabase? cache;
  final NotificationSink notifications;
  final PermissionService permissions;
  final ForegroundKeepaliveService keepalive;
  final CallMedia media;
  final MediaPicker? picker;
  final DateTime Function() now;
  final drafts = DraftSync();
  final _uuid = const Uuid();

  late ProtocolEngine engine;
  late CallController calls;
  WsService? socket;

  ServerEndpoints endpoints = ServerEndpoints.defaults;
  String? token;
  Map<String, dynamic>? user;
  StoredIdentity? identity;
  String? deviceId;
  String deviceName = 'Flutter Android';
  String? error;
  String? kickMessage;
  bool busy = false;
  bool bootstrapped = false;
  String? activeConversationId;
  final peerKeys = <String, Map<String, dynamic>>{};
  final verifiedFingerprints = <String, String>{};
  List<Map<String, dynamic>> friends = [];
  List<Map<String, dynamic>> friendRequests = [];
  List<Map<String, dynamic>> friendGroups = [];
  List<Map<String, dynamic>> groups = [];
  List<Map<String, dynamic>> devices = [];
  List<Map<String, dynamic>> emojis = [];
  List<Map<String, dynamic>> searchHits = [];
  Map<String, dynamic>? announcementReaders;
  bool hideOnline = false;

  void refresh() => notifyListeners();

  String get selfId => user?['user_id']?.toString() ?? engine.selfUserId;
  bool get loggedIn => token != null && token!.isNotEmpty && kickMessage == null;

  Future<void> bootstrap() async {
    endpoints = await secure.readEndpoints();
    api.setBaseUrl(endpoints.normalizedHttp);
    deviceId = await secure.deviceId();
    deviceName = await secure.deviceName();
    identity = await secure.readIdentity();
    verifiedFingerprints.addAll(await secure.readVerified());
    token = await secure.readToken();
    user = await secure.readUser();
    final id = selfId;
    if (id.isNotEmpty) {
      engine = ProtocolEngine(selfUserId: id);
      if (cache != null) {
        for (final conversation in await cache!.loadConversations(id)) {
          engine.conversations[conversation.conversationId] = conversation;
        }
      }
    }
    bootstrapped = true;
    notifyListeners();
    if (loggedIn) {
      await _connect();
      unawaited(refreshConversations());
    }
  }

  Future<void> saveEndpoints(ServerEndpoints value) async {
    endpoints = value;
    api.setBaseUrl(value.normalizedHttp);
    await secure.writeEndpoints(value);
    notifyListeners();
  }

  Future<String?> login(String username, String password) async {
    busy = true;
    error = null;
    kickMessage = null;
    notifyListeners();
    try {
      deviceId = await secure.deviceId();
      final session = await api.login(
        username: username.trim(),
        password: password,
        deviceId: deviceId!,
        deviceName: deviceName,
      );
      token = session.accessToken;
      user = session.user;
      if (session.deviceId != null && session.deviceId!.isNotEmpty) {
        deviceId = session.deviceId;
        await secure.store.write(SecureSessionStore.deviceIdKey, deviceId);
      }
      await secure.writeToken(token);
      await secure.writeUser(user);
      engine = ProtocolEngine(selfUserId: selfId);
      await _connect();
      await refreshConversations();
      await permissions.ensureNotifications();
      return null;
    } on ApiException catch (caught) {
      error = caught.message;
      return caught.message;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<String?> register(String username, String password, String nickname) async {
    try {
      await api.register(username: username.trim(), password: password, nickname: nickname);
      return await login(username, password);
    } on ApiException catch (caught) {
      error = caught.message;
      notifyListeners();
      return caught.message;
    }
  }

  Future<void> logout() async {
    try {
      await api.logout();
    } catch (_) {}
    await _clearLocal(kicked: false);
  }

  Future<void> refreshConversations() async {
    if (!loggedIn) return;
    final list = await api.conversations();
    engine.applyConversationList(list);
    final id = selfId;
    if (cache != null) {
      for (final conversation in engine.conversations.values) {
        await cache!.saveConversation(id, conversation);
      }
    }
    notifyListeners();
  }

  Future<void> openConversation(String conversationId) async {
    activeConversationId = conversationId;
    engine.activeConversationId = conversationId;
    final conversation = engine.conversations[conversationId];
    if (conversation != null) {
      conversation.unreadCount = 0;
      conversation.atMe = false;
      conversation.atAll = false;
    }
    if (cache != null && selfId.isNotEmpty) {
      final cached = await cache!.loadMessages(selfId, conversationId);
      for (final message in cached) {
        engine.messages[message.messageId] = message;
        await _decryptRecord(message);
      }
      final draft = await cache!.loadDraft(selfId, conversationId);
      if (draft != null) engine.drafts[conversationId] = draft;
    }
    notifyListeners();
    if (!loggedIn) return;
    try {
      final group = conversationId.startsWith('grp_') || conversation?.type == 'group';
      final targetId = group
          ? conversationId
          : (conversation?.peerId ?? otherParticipant(conversationId, selfId));
      if (targetId != null && targetId.isNotEmpty) {
        final created = await api.createConversation(
          targetId: targetId,
          type: group ? 'group' : 'private',
        );
        if (created.isNotEmpty) {
          engine.applyConversationList([created]);
          final conv = engine.conversations[conversationId];
          if (cache != null && selfId.isNotEmpty && conv != null) {
            await cache!.saveConversation(selfId, conv);
          }
        }
      }
    } catch (_) {}
    try {
      final history = await api.history(conversationId);
      for (final item in history) {
        engine.applyFrame({'type': framePrivateResponse, 'message': item});
        final msgId = item['message_id']?.toString();
        if (msgId != null && engine.messages[msgId] != null) {
          await _decryptRecord(engine.messages[msgId]!);
        }
      }
      final latest = engine.conversations[conversationId]?.latestSeq ?? '0';
      if (latest != '0') {
        socket?.send(wsFrame(frameReadReceipt, {'conversation_id': conversationId, 'read_seq': latest}));
      }
    } catch (_) {}
    notifyListeners();
  }

  Future<void> sendText(String conversationId, String text, {String? replyTo, List<String> atUsers = const []}) async {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return;
    final conversation = engine.conversations[conversationId] ?? engine.conversations.putIfAbsent(
      conversationId,
      () => ConversationRecord(conversationId: conversationId, type: conversationId.startsWith('grp_') ? 'group' : 'private'),
    );
    var content = trimmed;
    var encrypted = false;
    if (conversation.e2eeEnabled && !conversationId.startsWith('grp_')) {
      final envelope = await _encrypt(conversationId, trimmed);
      if (envelope == null) {
        error = '还没有对方的加密公钥';
        notifyListeners();
        return;
      }
      content = envelope;
      encrypted = true;
    }
    final clientId = 'c_${_uuid.v4()}';
    final local = MessageRecord(
      messageId: clientId,
      conversationId: conversationId,
      senderId: selfId,
      messageType: 'text',
      content: content,
      clientMessageId: clientId,
      deliveryState: 'sending',
      timestampMs: now().millisecondsSinceEpoch,
      isEncrypted: encrypted,
      localPlaintext: encrypted ? trimmed : null,
      atUsers: atUsers,
      replyTo: replyTo == null
          ? null
          : ReplySnapshot(messageId: replyTo, previewText: engine.messages[replyTo]?.localPlaintext ?? engine.messages[replyTo]?.content),
    );
    engine.addLocalMessage(local);
    final group = conversationId.startsWith('grp_') || conversation.type == 'group';
    final frame = wsFrame(group ? frameGroupRequest : framePrivateRequest, {
      if (!group) 'conversation_id': conversationId,
      if (!group) 'receiver_id': conversation.peerId ?? otherParticipant(conversationId, selfId),
      if (group) 'group_id': conversationId,
      'message_type': 'text',
      'content': content,
      'client_message_id': clientId,
      if (replyTo != null) 'reply_to_message_id': replyTo,
      if (group && atUsers.isNotEmpty) 'at_users': atUsers,
      if (encrypted) 'is_encrypted': true,
    });
    socket?.send(frame);
    await cache?.saveMessage(selfId, local);
    drafts.of(conversationId).content = '';
    await cache?.saveDraft(selfId, DraftRecord(conversationId: conversationId, content: '', cleared: true));
    if (loggedIn) unawaited(api.clearDraft(conversationId).catchError((_) {}));
    notifyListeners();
  }

  Future<void> sendMedia(String conversationId, PickedMedia media) async {
    if (!loggedIn) return;
    final uploaded = await api.upload(type: media.kind, bytes: media.bytes, filename: media.filename, durationMs: media.durationMs);
    final clientId = 'c_${_uuid.v4()}';
    final type = media.kind == 'voice' ? 'voice' : media.kind;
    final local = MessageRecord(
      messageId: clientId,
      conversationId: conversationId,
      senderId: selfId,
      messageType: type,
      content: jsonEncode(uploaded),
      clientMessageId: clientId,
      deliveryState: 'sending',
      timestampMs: now().millisecondsSinceEpoch,
    );
    engine.addLocalMessage(local);
    final group = conversationId.startsWith('grp_');
    socket?.send(wsFrame(group ? frameGroupRequest : framePrivateRequest, {
      if (!group) 'conversation_id': conversationId,
      if (!group) 'receiver_id': engine.conversations[conversationId]?.peerId ?? otherParticipant(conversationId, selfId),
      if (group) 'group_id': conversationId,
      'message_type': type,
      'content': uploaded['file_id'] ?? jsonEncode(uploaded),
      'client_message_id': clientId,
    }));
    notifyListeners();
  }

  Future<void> recall(String messageId) async {
    final message = engine.messages[messageId];
    if (message == null) return;
    socket?.send(wsFrame(frameRecallRequest, {
      'message_id': messageId,
      'conversation_id': message.conversationId,
      'client_message_id': message.clientMessageId,
    }));
    try {
      await api.recall(messageId);
    } catch (_) {}
  }

  Future<void> edit(String messageId, String content, {List<String> atUsers = const []}) async {
    final message = engine.messages[messageId];
    if (message == null) return;
    var next = content.trim();
    var encrypted = message.isEncrypted;
    if (encrypted) {
      final envelope = await _encrypt(message.conversationId, next);
      if (envelope == null) return;
      next = envelope;
    }
    socket?.send(wsFrame(frameEditRequest, {
      'message_id': messageId,
      'content': next,
      'at_users': atUsers,
      'client_message_id': message.clientMessageId,
      if (encrypted) 'is_encrypted': true,
    }));
    message.content = next;
    message.localPlaintext = encrypted ? content.trim() : null;
    message.edited = true;
    message.contentRevision += 1;
    notifyListeners();
  }

  Future<void> forward({
    required String sourceConversationId,
    required List<String> messageIds,
    required List<String> targets,
    String mode = 'separate',
  }) async {
    final result = await api.forward({
      'source_conversation_id': sourceConversationId,
      'message_ids': messageIds,
      'targets': targets,
      'mode': mode,
      'client_request_id': 'fwd_${_uuid.v4().replaceAll('-', '').substring(0, 16)}',
    });
    final results = result['results'];
    if (results is List) {
      for (final item in results.whereType<Map>()) {
        final messages = item['messages'];
        if (messages is List) {
          for (final message in messages.whereType<Map>()) {
            engine.applyFrame({'type': framePrivateResponse, 'message': message.map((key, value) => MapEntry(key.toString(), value))});
          }
        }
      }
    }
    notifyListeners();
  }

  Future<void> search(String keyword, {String? conversationId}) async {
    try {
      final result = await api.searchMessages(query: keyword, conversationId: conversationId);
      final results = result['results'];
      searchHits = results is List
          ? results.whereType<Map>().map((item) {
              final map = item.map((key, value) => MapEntry(key.toString(), value));
              final nested = map['message'];
              if (nested is Map) {
                return {
                  ...nested.map((key, value) => MapEntry(key.toString(), value)),
                  ...map,
                };
              }
              return map;
            }).toList()
          : [];
    } catch (_) {
      searchHits = [];
    }
    notifyListeners();
  }

  Future<void> togglePin(String id) async {
    final conversation = engine.conversations[id];
    if (conversation == null) return;
    conversation.pinned = !conversation.pinned;
    notifyListeners();
    if (loggedIn) await api.pin(id, pinned: conversation.pinned);
  }

  Future<void> toggleMute(String id) async {
    final conversation = engine.conversations[id];
    if (conversation == null) return;
    conversation.muted = !conversation.muted;
    notifyListeners();
    if (loggedIn) await api.mute(id, muted: conversation.muted);
  }

  Future<void> removeConversation(String id) async {
    engine.conversations.remove(id);
    notifyListeners();
    if (loggedIn) await api.deleteConversation(id);
    if (selfId.isNotEmpty) await cache?.deleteConversation(selfId, id);
  }

  Future<void> loadContacts() async {
    if (!loggedIn) return;
    try {
      friends = await api.friends();
      friendRequests = await api.friendRequests();
      friendGroups = await api.friendGroups();
      groups = await api.groups();
    } catch (_) {
      return;
    }
    final ids = friends.map((item) => item['friend_id']?.toString() ?? item['user_id']?.toString()).whereType<String>().toList();
    if (ids.isNotEmpty) {
      final snapshot = await api.presence(ids);
      final list = snapshot['presences'];
      if (list is List) {
        for (final item in list.whereType<Map>()) {
          engine.applyFrame({'type': framePresence, 'message': item.map((key, value) => MapEntry(key.toString(), value))});
        }
      }
    }
    notifyListeners();
  }

  Future<void> loadDevices() async {
    try {
      devices = await api.devices();
    } catch (_) {
      devices = [];
    }
    notifyListeners();
  }

  Future<void> logoutDevice(String id) async {
    await api.logoutDevice(id);
    devices.removeWhere((item) => item['device_id'] == id);
    notifyListeners();
  }

  Future<String?> changePassword(String oldPassword, String newPassword) async {
    try {
      await api.changePassword(oldPassword: oldPassword, newPassword: newPassword);
      return null;
    } on ApiException catch (caught) {
      return caught.message;
    }
  }

  Future<String> exportBackup(String passphrase) async {
    identity ??= await generateIdentity();
    await secure.writeIdentity(identity);
    await _publishKeys();
    return exportKeyBackup(identity!, passphrase);
  }

  Future<String?> importBackup(String payload, String passphrase) async {
    try {
      identity = await importKeyBackup(payload, passphrase);
      await secure.writeIdentity(identity);
      await _publishKeys();
      await _decryptAllLoadedMessages();
      notifyListeners();
      return null;
    } on StateError catch (caught) {
      return caught.message;
    }
  }

  Future<void> toggleE2ee(String conversationId, bool enabled) async {
    if (enabled) {
      identity ??= await generateIdentity();
      await secure.writeIdentity(identity);
      await _publishKeys();
      final peer = engine.conversations[conversationId]?.peerId ?? otherParticipant(conversationId, selfId);
      if (peer != null) {
        peerKeys[peer] = await api.peerKeys(peer);
      }
    }
    final state = await api.setE2ee(conversationId, enabled: enabled);
    engine.conversations[conversationId]?.e2eeEnabled = state['enabled'] == true || enabled;
    notifyListeners();
  }

  Future<String?> safetyCode(String conversationId) async {
    if (identity == null) return null;
    final peer = engine.conversations[conversationId]?.peerId ?? otherParticipant(conversationId, selfId);
    if (peer == null) return null;
    peerKeys[peer] ??= await api.peerKeys(peer);
    final fingerprint = peerKeys[peer]?['fingerprint']?.toString();
    if (fingerprint == null) return null;
    return formatSafetyNumber(await safetyNumber(identity!.fingerprint, fingerprint));
  }

  Future<void> markVerified(String peerId, String fingerprint) async {
    verifiedFingerprints[peerId] = fingerprint;
    await secure.writeVerified(verifiedFingerprints);
    if (selfId.isNotEmpty) await cache?.saveVerifiedFingerprint(selfId, peerId, fingerprint);
    notifyListeners();
  }

  Future<void> confirmAnnouncement(String groupId, String announcementId) async {
    await api.confirmAnnouncement(groupId, announcementId);
    engine.announcements['$groupId:$announcementId']?.readByMe = true;
    notifyListeners();
  }

  Future<void> loadAnnouncementReaders(String groupId, String announcementId) async {
    announcementReaders = await api.announcementReaders(groupId, announcementId);
    notifyListeners();
  }

  Future<void> loadEmojis() async {
    final data = await api.emojis();
    final list = data['emojis'];
    emojis = list is List ? list.whereType<Map>().map((item) => item.map((key, value) => MapEntry(key.toString(), value))).toList() : [];
    notifyListeners();
  }

  Future<void> deleteEmoji(String emojiId) async {
    await api.deleteEmoji(emojiId);
    emojis.removeWhere((item) => item['emoji_id'] == emojiId);
    notifyListeners();
  }

  Future<void> startCall(String conversationId, String callType) async {
    final allowed = await permissions.ensureCall(video: callType == 'video');
    if (!allowed) return;
    final peer = engine.conversations[conversationId]?.peerId ?? otherParticipant(conversationId, selfId);
    if (peer == null) return;
    final sdp = await media.createOffer(callType: callType);
    final callId = 'call_${_uuid.v4().replaceAll('-', '').substring(0, 16)}';
    calls.invite(callId: callId, targetId: peer, conversationId: conversationId, callType: callType, sdp: sdp);
    _flushCallSignals();
    notifyListeners();
  }

  Future<void> answerCall() async {
    final allowed = await permissions.ensureCall(video: calls.state.callType == 'video');
    if (!allowed || calls.state.remoteSdp == null) return;
    final sdp = await media.createAnswer(calls.state.remoteSdp!, callType: calls.state.callType);
    calls.answer(sdp);
    _flushCallSignals();
    notifyListeners();
  }

  void rejectCall() {
    calls.reject();
    _flushCallSignals();
    notifyListeners();
  }

  void hangupCall() {
    if (calls.state.phase == CallPhase.outgoing || calls.state.phase == CallPhase.incoming) {
      calls.cancel();
    } else {
      calls.hangup();
    }
    _flushCallSignals();
    notifyListeners();
  }

  Future<void> onLifecycle(AppLifecycleState state) => keepalive.onLifecycle(state, loggedIn: loggedIn);

  Future<void> noteDraft(String conversationId, String content) async {
    final changed = drafts.noteLocalEdit(conversationId, content, now().millisecondsSinceEpoch);
    if (!changed) return;
    engine.conversations[conversationId]?.draftPreview = content.trim().isEmpty ? null : draftPreviewOf(content);
    await cache?.saveDraft(
      selfId,
      DraftRecord(conversationId: conversationId, content: content, updatedAt: now().millisecondsSinceEpoch),
    );
    notifyListeners();
  }

  @override
  void dispose() {
    unawaited(socket?.dispose());
    super.dispose();
  }

  Future<void> _connect() async {
    await socket?.dispose();
    socket = WsService(
      endpoints: endpoints,
      token: token ?? '',
      deviceId: deviceId ?? 'phone',
      sessionProbe: () async {
        try {
          await api.me();
          return true;
        } on ApiException catch (caught) {
          return caught.status != 401;
        } catch (_) {
          return true;
        }
      },
      onKicked: (message) {
        unawaited(_clearLocal(kicked: true, message: message));
      },
    );
    socket!.frames.stream.listen(_onFrame);
    await socket!.connect();
    socket!.send(engine.buildSyncRequest());
  }

  void _onFrame(Map<String, dynamic> frame) {
    final rawType = frame['type']?.toString() ?? '';
    final type = canonicalFrameType(rawType);
    if (type == frameSessionKicked) return;
    engine.applyFrame(frame);
    if (type.startsWith('CALL_')) {
      calls.applySignal(frame);
      _flushCallSignals();
      if (calls.state.phase == CallPhase.incoming) {
        unawaited(notifications.showIncomingCall(
          callId: calls.state.callId ?? '',
          title: '来电',
          body: calls.state.callType == 'audio' ? '语音通话' : '视频通话',
        ));
      }
    }
    if (type == framePrivateResponse || type == frameGroupResponse || type == frameMessageEdited) {
      final message = frame['message'];
      if (message is Map) {
        final msgId = message['message_id']?.toString();
        if (msgId != null && engine.messages[msgId] != null) {
          unawaited(_decryptRecord(engine.messages[msgId]!).then((_) => notifyListeners()));
        }
      }
    } else if (type == frameOfflineSync) {
      unawaited(_decryptAllLoadedMessages().then((_) => notifyListeners()));
    }
    if (type == framePrivateResponse || type == frameGroupResponse) {
      final message = frame['message'];
      if (message is Map && message['sender_id']?.toString() != selfId) {
        final conversationId = message['conversation_id']?.toString() ?? '';
        if (conversationId != activeConversationId) {
          unawaited(notifications.showMessage(
            conversationId: conversationId,
            title: engine.conversations[conversationId]?.name ?? '新消息',
            body: messagePreview(
              messageType: message['message_type']?.toString() ?? 'text',
              content: message['content']?.toString() ?? '',
              encrypted: message['is_encrypted'] == true,
            ),
          ));
        } else {
          socket?.send(wsFrame(frameReceipt, {
            'receipt_type': 'delivered',
            'message_id': message['message_id'],
            'conversation_id': conversationId,
            'seq_id': message['seq_id'],
          }));
        }
      }
    }
    if (selfId.isNotEmpty && cache != null) {
      final id = frame['message'] is Map ? (frame['message'] as Map)['message_id']?.toString() : null;
      final stored = id == null ? null : engine.messages[id];
      if (stored != null) unawaited(cache!.saveMessage(selfId, stored));
    }
    notifyListeners();
  }

  void _flushCallSignals() {
    for (final frame in calls.outbound) {
      socket?.send(frame);
    }
    calls.outbound.clear();
  }

  Future<String?> _encrypt(String conversationId, String plaintext) async {
    identity ??= await secure.readIdentity();
    if (identity == null) return null;
    final peer = engine.conversations[conversationId]?.peerId ?? otherParticipant(conversationId, selfId);
    if (peer == null) return null;
    peerKeys[peer] ??= await api.peerKeys(peer);
    final peerKey = peerKeys[peer]!;
    final envelope = await encryptText(
      plaintext: plaintext,
      senderPrivateJwk: identity!.identityPrivateJwk,
      senderKeyId: identity!.keyId,
      recipientPublicSpki: peerKey['identity_public_key']?.toString() ?? '',
      recipientKeyId: peerKey['key_id']?.toString() ?? '',
      counter: now().millisecondsSinceEpoch,
    );
    return envelope.encode();
  }

  Future<void> _decryptRecord(MessageRecord record) async {
    if (!record.isEncrypted || record.isRecalled) return;
    if (record.localPlaintext != null && record.localPlaintext!.isNotEmpty) return;
    final envelope = parseEnvelope(record.content);
    if (envelope == null) return;
    identity ??= await secure.readIdentity();
    if (identity == null) return;
    final peer = engine.conversations[record.conversationId]?.peerId ?? otherParticipant(record.conversationId, selfId);
    if (peer == null || peer.isEmpty) return;
    try {
      peerKeys[peer] ??= await api.peerKeys(peer);
      final peerSpki = peerKeys[peer]?['identity_public_key']?.toString();
      if (peerSpki == null || peerSpki.isEmpty) return;
      final clear = await decryptText(
        envelope: envelope,
        myPrivateJwk: identity!.identityPrivateJwk,
        peerPublicSpki: peerSpki,
      );
      record.localPlaintext = clear;
      if (selfId.isNotEmpty && cache != null) {
        await cache!.saveMessage(selfId, record);
      }
    } catch (_) {}
  }

  Future<void> _decryptAllLoadedMessages() async {
    for (final record in engine.messages.values.toList()) {
      await _decryptRecord(record);
    }
  }

  Future<void> _publishKeys() async {
    final current = identity;
    if (current == null || !loggedIn) return;
    final signature = await signFingerprint(current);
    await api.uploadE2eeKeys({
      'key_id': current.keyId,
      'identity_public_key': current.identityPublicSpki,
      'signing_public_key': current.signingPublicSpki,
      'algorithm': current.algorithm,
      'fingerprint': current.fingerprint,
      'signature': signature,
    });
  }

  Future<void> _clearLocal({required bool kicked, String? message}) async {
    token = null;
    user = null;
    kickMessage = kicked ? message : null;
    socket?.logout();
    await secure.clearSession();
    await keepalive.stop();
    activeConversationId = null;
    notifyListeners();
  }
}
