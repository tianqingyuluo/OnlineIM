import 'dart:convert';

import 'conversation_id.dart';
import 'frame_types.dart';
import 'preview_text.dart';
import 'seq.dart';

class ReplySnapshot {
  ReplySnapshot({
    required this.messageId,
    this.seqId,
    this.senderId,
    this.senderDisplayName,
    this.messageType,
    this.previewText,
    this.state = 'active',
  });

  final String messageId;
  final String? seqId;
  final String? senderId;
  final String? senderDisplayName;
  final String? messageType;
  String? previewText;
  String state;

  factory ReplySnapshot.fromJson(Map<String, dynamic> json) {
    return ReplySnapshot(
      messageId: json['message_id']?.toString() ?? '',
      seqId: json['seq_id']?.toString(),
      senderId: json['sender_id']?.toString(),
      senderDisplayName: json['sender_display_name']?.toString(),
      messageType: json['message_type']?.toString(),
      previewText: json['preview_text']?.toString(),
      state: json['state']?.toString() ?? 'active',
    );
  }

  Map<String, dynamic> toJson() => {
        'message_id': messageId,
        if (seqId != null) 'seq_id': seqId,
        if (senderId != null) 'sender_id': senderId,
        if (senderDisplayName != null) 'sender_display_name': senderDisplayName,
        if (messageType != null) 'message_type': messageType,
        'preview_text': previewText,
        'state': state,
      };
}

class MessageRecord {
  MessageRecord({
    required this.messageId,
    required this.conversationId,
    required this.senderId,
    required this.messageType,
    required this.content,
    this.clientMessageId,
    this.seqId,
    this.deliveryState = 'sent',
    this.timestampMs,
    this.senderName,
    this.replyTo,
    this.atUsers = const [],
    this.forwardedFrom,
    this.edited = false,
    this.editedAt,
    this.contentRevision = 1,
    this.isRecalled = false,
    this.isEncrypted = false,
    this.recallUserId,
    this.recallOperatorRole,
    this.recalledPlaintext,
    this.atMe = false,
    this.atAll = false,
    this.localPlaintext,
  });

  String messageId;
  final String conversationId;
  String senderId;
  String messageType;
  String content;
  String? clientMessageId;
  String? seqId;
  String deliveryState;
  int? timestampMs;
  String? senderName;
  ReplySnapshot? replyTo;
  List<String> atUsers;
  Map<String, dynamic>? forwardedFrom;
  bool edited;
  String? editedAt;
  int contentRevision;
  bool isRecalled;
  bool isEncrypted;
  String? recallUserId;
  String? recallOperatorRole;
  String? recalledPlaintext;
  bool atMe;
  bool atAll;
  String? localPlaintext;

  bool get canReedit =>
      isRecalled &&
      recalledPlaintext != null &&
      messageType == 'text' &&
      recallOperatorRole == null;

  Map<String, dynamic> toJson() => {
        'message_id': messageId,
        'conversation_id': conversationId,
        'sender_id': senderId,
        'message_type': messageType,
        'content': content,
        if (clientMessageId != null) 'client_message_id': clientMessageId,
        if (seqId != null) 'seq_id': seqId,
        'delivery_state': deliveryState,
        if (timestampMs != null) 'timestamp_ms': timestampMs,
        if (senderName != null) 'sender_name': senderName,
        if (replyTo != null) 'reply_to': replyTo!.toJson(),
        'at_users': atUsers,
        if (forwardedFrom != null) 'forwarded_from': forwardedFrom,
        'edited': edited,
        if (editedAt != null) 'edited_at': editedAt,
        'content_revision': contentRevision,
        'is_recalled': isRecalled,
        'is_encrypted': isEncrypted,
        if (recallUserId != null) 'recall_user_id': recallUserId,
        if (recallOperatorRole != null) 'recall_operator_role': recallOperatorRole,
        if (recalledPlaintext != null) 'recalled_plaintext': recalledPlaintext,
        'at_me': atMe,
        'at_all': atAll,
        if (localPlaintext != null) 'local_plaintext': localPlaintext,
      };

  factory MessageRecord.fromJson(Map<String, dynamic> json) {
    return MessageRecord(
      messageId: json['message_id']?.toString() ?? '',
      conversationId: json['conversation_id']?.toString() ?? '',
      senderId: json['sender_id']?.toString() ?? '',
      messageType: json['message_type']?.toString() ?? 'text',
      content: json['content']?.toString() ?? '',
      clientMessageId: json['client_message_id']?.toString(),
      seqId: json['seq_id']?.toString(),
      deliveryState: json['delivery_state']?.toString() ?? 'sent',
      timestampMs: json['timestamp_ms'] is num ? (json['timestamp_ms'] as num).toInt() : _parseTime(json['timestamp']),
      senderName: json['sender_name']?.toString() ?? _senderName(json),
      replyTo: json['reply_to'] is Map ? ReplySnapshot.fromJson(_map(json['reply_to'])) : null,
      atUsers: _stringList(json['at_users']),
      forwardedFrom: json['forwarded_from'] is Map ? _map(json['forwarded_from']) : null,
      edited: json['edited'] == true,
      editedAt: json['edited_at']?.toString(),
      contentRevision: json['content_revision'] is num ? (json['content_revision'] as num).toInt() : 1,
      isRecalled: json['is_recalled'] == true || json['status'] == 3,
      isEncrypted: json['is_encrypted'] == true,
      recallUserId: json['recall_user_id']?.toString(),
      recallOperatorRole: json['recall_operator_role']?.toString(),
      recalledPlaintext: json['recalled_plaintext']?.toString(),
      atMe: json['at_me'] == true,
      atAll: json['at_all'] == true,
      localPlaintext: json['local_plaintext']?.toString(),
    );
  }
}

class ConversationRecord {
  ConversationRecord({
    required this.conversationId,
    this.type = 'private',
    this.name = '',
    this.avatarUrl,
    this.peerId,
    this.lastPreview = '',
    this.lastMessageType,
    this.lastSenderId,
    this.lastSenderName,
    this.lastRecalled = false,
    this.latestSeq = '0',
    this.deliveredSeq = '0',
    this.readSeq = '0',
    this.syncSeq = '0',
    this.unreadCount = 0,
    this.pinned = false,
    this.muted = false,
    this.draftPreview,
    this.atMe = false,
    this.atMeCount = 0,
    this.atMeMessageId,
    this.atAll = false,
    this.atAllCount = 0,
    this.atPersonal = false,
    this.e2eeEnabled = false,
    this.updatedAt = 0,
    this.myRole,
  });

  final String conversationId;
  String type;
  String name;
  String? avatarUrl;
  String? peerId;
  String lastPreview;
  String? lastMessageType;
  String? lastSenderId;
  String? lastSenderName;
  bool lastRecalled;
  String latestSeq;
  String deliveredSeq;
  String readSeq;
  String syncSeq;
  int unreadCount;
  bool pinned;
  bool muted;
  String? draftPreview;
  bool atMe;
  int atMeCount;
  String? atMeMessageId;
  bool atAll;
  int atAllCount;
  bool atPersonal;
  bool e2eeEnabled;
  int updatedAt;
  String? myRole;

  Map<String, dynamic> toJson() => {
        'conversation_id': conversationId,
        'type': type,
        'name': name,
        'avatar_url': avatarUrl,
        'peer_id': peerId,
        'last_preview': lastPreview,
        'last_message_type': lastMessageType,
        'last_sender_id': lastSenderId,
        'last_sender_name': lastSenderName,
        'last_recalled': lastRecalled,
        'latest_seq': latestSeq,
        'delivered_seq': deliveredSeq,
        'read_seq': readSeq,
        'sync_seq': syncSeq,
        'unread_count': unreadCount,
        'pinned': pinned,
        'muted': muted,
        'draft_preview': draftPreview,
        'at_me': atMe,
        'at_me_count': atMeCount,
        'at_me_message_id': atMeMessageId,
        'at_all': atAll,
        'at_all_count': atAllCount,
        'at_personal': atPersonal,
        'e2ee_enabled': e2eeEnabled,
        'updated_at': updatedAt,
        'my_role': myRole,
      };

  factory ConversationRecord.fromJson(Map<String, dynamic> json) {
    return ConversationRecord(
      conversationId: json['conversation_id']?.toString() ?? '',
      type: json['type']?.toString() ?? json['conversation_type']?.toString() ?? 'private',
      name: json['name']?.toString() ?? '',
      avatarUrl: json['avatar_url']?.toString(),
      peerId: json['peer_id']?.toString() ?? json['target_id']?.toString(),
      lastPreview: json['last_preview']?.toString() ?? _previewFromLast(json),
      lastMessageType: json['last_message_type']?.toString(),
      lastSenderId: json['last_sender_id']?.toString(),
      lastSenderName: json['last_sender_name']?.toString(),
      lastRecalled: json['last_recalled'] == true,
      latestSeq: json['latest_seq']?.toString() ?? '0',
      deliveredSeq: json['delivered_seq']?.toString() ?? '0',
      readSeq: json['read_seq']?.toString() ?? '0',
      syncSeq: json['sync_seq']?.toString() ?? json['latest_seq']?.toString() ?? '0',
      unreadCount: json['unread_count'] is num ? (json['unread_count'] as num).toInt() : 0,
      pinned: json['pinned'] == true || json['is_top'] == true,
      muted: json['muted'] == true || json['is_muted'] == true,
      draftPreview: json['draft_preview']?.toString(),
      atMe: json['at_me'] == true,
      atMeCount: json['at_me_count'] is num ? (json['at_me_count'] as num).toInt() : 0,
      atMeMessageId: json['at_me_message_id']?.toString(),
      atAll: json['at_all'] == true,
      atAllCount: json['at_all_count'] is num ? (json['at_all_count'] as num).toInt() : 0,
      atPersonal: json['at_personal'] == true,
      e2eeEnabled: json['e2ee_enabled'] == true,
      updatedAt: json['updated_at'] is num ? (json['updated_at'] as num).toInt() : 0,
      myRole: json['my_role']?.toString(),
    );
  }
}

class PresenceRecord {
  PresenceRecord({required this.userId, required this.status, this.lastSeenAt, this.visible = true});

  final String userId;
  String status;
  int? lastSeenAt;
  bool visible;

  bool get online => visible && status == 'online';
}

class DraftRecord {
  DraftRecord({
    required this.conversationId,
    required this.content,
    this.replyToMessageId,
    this.replyPreview,
    this.atUsers = const [],
    this.updatedAt = 0,
    this.cleared = false,
  });

  final String conversationId;
  String content;
  String? replyToMessageId;
  String? replyPreview;
  List<Map<String, dynamic>> atUsers;
  int updatedAt;
  bool cleared;

  String get preview => draftPreviewOf(content);
}

class AnnouncementRecord {
  AnnouncementRecord({
    required this.groupId,
    required this.announcementId,
    this.title = '',
    this.content = '',
    this.action = 'published',
    this.actorId,
    this.readByMe = false,
    this.pinned = false,
    this.readCount,
    this.unreadCount,
    this.deleted = false,
  });

  final String groupId;
  final String announcementId;
  String title;
  String content;
  String action;
  String? actorId;
  bool readByMe;
  bool pinned;
  int? readCount;
  int? unreadCount;
  bool deleted;
}

class ProtocolEngine {
  ProtocolEngine({required this.selfUserId});

  final String selfUserId;
  final Map<String, ConversationRecord> conversations = {};
  final Map<String, MessageRecord> messages = {};
  final Map<String, PresenceRecord> presence = {};
  final Map<String, DraftRecord> drafts = {};
  final Map<String, AnnouncementRecord> announcements = {};
  final List<Map<String, dynamic>> callSignals = [];
  final Map<String, String> memberReadSeq = {};

  String? activeConversationId;
  String? kickReason;
  String? kickMessage;
  bool sessionCleared = false;
  bool connectedHandshake = false;
  bool syncTruncated = false;
  bool stopReconnect = false;

  void applyFrame(Map<String, dynamic> frame) {
    final rawType = frame['type']?.toString() ?? '';
    final type = canonicalFrameType(rawType);
    final payload = _payload(frame);
    final atMe = frame['at_me'] == true;
    final atAll = frame['at_all'] == true;
    switch (type) {
      case frameConnectResponse:
        connectedHandshake = true;
      case frameHeartbeat:
      case frameHeartbeatAck:
        break;
      case frameError:
        _applyError(payload);
      case frameMessageAck:
        _applyAck(payload);
      case frameReceipt:
        _applyReceipt(payload);
      case frameReadReceipt:
        _applyRead(payload);
      case framePrivateResponse:
      case frameGroupResponse:
        _upsertMessage(payload, atMe: atMe, atAll: atAll, countUnread: true);
      case frameMessageEdited:
        _applyEdit(payload, atMe: atMe, atAll: atAll);
      case frameMessageRecalled:
      case frameRecallResponse:
        _applyRecall(payload);
      case frameOfflineSync:
        _applyOfflineSync(payload);
      case frameDraftUpdated:
        _applyDraft(payload);
      case framePresence:
        _applyPresence(payload);
      case frameSessionKicked:
        kickReason = payload['reason']?.toString();
        kickMessage = payload['message']?.toString() ?? '您的账号已在其他设备登录，当前设备已下线';
        sessionCleared = true;
        stopReconnect = true;
      case frameE2ee:
        final id = payload['conversation_id']?.toString();
        if (id != null) {
          _conversation(id).e2eeEnabled = payload['enabled'] == true;
        }
      case frameAnnouncement:
        _applyAnnouncement(payload);
      case frameAnnouncementRead:
        _applyAnnouncementRead(payload);
      case frameCallInvite:
      case frameCallOffer:
      case frameCallRinging:
      case frameCallAnswer:
      case frameCallCandidate:
      case frameCallReject:
      case frameCallCancel:
      case frameCallBusy:
      case frameCallEnd:
      case frameCallHangup:
      case frameCallHandledElsewhere:
        callSignals.add({'type': type, ...payload});
      default:
        break;
    }
  }

  void applyConversationList(List<Map<String, dynamic>> items) {
    for (final item in items) {
      _mergeConversationSnapshot(item);
    }
  }

  MessageRecord addLocalMessage(MessageRecord message) {
    messages[message.messageId] = message;
    _touchPreview(message, countUnread: false);
    return message;
  }

  Map<String, dynamic> buildSyncRequest({Map<String, List<String>> watch = const {}}) {
    final cursors = conversations.values
        .map((item) => {
              'conversation_id': item.conversationId,
              'last_seq': item.syncSeq.isEmpty ? '0' : item.syncSeq,
            })
        .take(100)
        .toList();
    final watchPayload = <Map<String, dynamic>>[];
    var remaining = 100;
    watch.forEach((conversationId, ids) {
      if (remaining <= 0) return;
      final slice = ids.take(remaining).toList();
      remaining -= slice.length;
      watchPayload.add({'conversation_id': conversationId, 'message_ids': slice});
    });
    return wsFrame(frameSyncRequest, {
      'conversations': cursors,
      'watch': watchPayload,
    });
  }

  List<MessageRecord> history(String conversationId) {
    final list = messages.values.where((item) => item.conversationId == conversationId).toList();
    list.sort((a, b) => compareSeq(a.seqId, b.seqId));
    return list;
  }

  void _applyError(Map<String, dynamic> payload) {
    final clientId = payload['client_message_id']?.toString();
    if (clientId == null) return;
    for (final message in messages.values) {
      if (message.clientMessageId == clientId && message.deliveryState == 'sending') {
        message.deliveryState = 'failed';
      }
    }
  }

  void _applyAck(Map<String, dynamic> payload) {
    final clientId = payload['client_message_id']?.toString();
    MessageRecord? target;
    for (final message in messages.values) {
      if (clientId != null && message.clientMessageId == clientId) {
        target = message;
        break;
      }
    }
    if (target == null) return;
    final serverId = payload['message_id']?.toString();
    if (serverId != null && serverId.isNotEmpty && serverId != target.messageId) {
      messages.remove(target.messageId);
      target.messageId = serverId;
      messages[serverId] = target;
    }
    target.seqId = payload['seq_id']?.toString() ?? target.seqId;
    target.deliveryState = mergeDeliveryState(target.deliveryState, payload['delivery_state']?.toString() ?? 'sent');
    final conversation = _conversation(payload['conversation_id']?.toString() ?? target.conversationId);
    if (target.seqId != null) {
      conversation.latestSeq = maxSeq(conversation.latestSeq, target.seqId);
      conversation.syncSeq = maxSeq(conversation.syncSeq, target.seqId);
    }
  }

  void _applyReceipt(Map<String, dynamic> payload) {
    if (payload['receipt_type']?.toString() != 'delivered') return;
    final conversationId = payload['conversation_id']?.toString();
    final seq = payload['seq_id']?.toString();
    if (conversationId == null || seq == null) return;
    final conversation = _conversation(conversationId);
    conversation.deliveredSeq = maxSeq(conversation.deliveredSeq, seq);
    for (final message in messages.values) {
      if (message.conversationId != conversationId || message.senderId != selfUserId) continue;
      if (compareSeq(message.seqId, seq) <= 0) {
        message.deliveryState = mergeDeliveryState(message.deliveryState, 'delivered');
      }
    }
  }

  void _applyRead(Map<String, dynamic> payload) {
    final conversationId = payload['conversation_id']?.toString();
    final seq = payload['read_seq']?.toString();
    final reader = payload['reader_id']?.toString();
    if (conversationId == null || seq == null) return;
    final conversation = _conversation(conversationId);
    if (reader == null || reader == selfUserId) {
      conversation.readSeq = maxSeq(conversation.readSeq, seq);
      conversation.deliveredSeq = maxSeq(conversation.deliveredSeq, seq);
      _clearMentionsUpTo(conversation, seq);
      conversation.unreadCount = messages.values.where((message) {
        return message.conversationId == conversationId &&
            !message.isRecalled &&
            message.senderId != selfUserId &&
            compareSeq(message.seqId, conversation.readSeq) > 0;
      }).length;
      return;
    }
    memberReadSeq['$conversationId:$reader'] = maxSeq(memberReadSeq['$conversationId:$reader'], seq);
    if (conversation.type != 'group') {
      for (final message in messages.values) {
        if (message.conversationId != conversationId || message.senderId != selfUserId) continue;
        if (compareSeq(message.seqId, seq) <= 0) {
          message.deliveryState = mergeDeliveryState(message.deliveryState, 'read');
        }
      }
    }
  }

  void _upsertMessage(
    Map<String, dynamic> payload, {
    required bool atMe,
    required bool atAll,
    required bool countUnread,
  }) {
    final message = _messageFromPayload(payload, atMe: atMe, atAll: atAll);
    final existing = messages[message.messageId];
    if (existing != null && message.contentRevision < existing.contentRevision) {
      return;
    }
    if (existing != null) {
      message.deliveryState = mergeDeliveryState(existing.deliveryState, message.deliveryState);
      message.recalledPlaintext ??= existing.recalledPlaintext;
      message.localPlaintext ??= existing.localPlaintext;
      if (existing.isRecalled && !message.isRecalled) {
        message.isRecalled = true;
        message.content = '';
      }
    }
    messages[message.messageId] = message;
    if (message.isRecalled) {
      _cascadeRecall(message);
    }
    _touchPreview(message, countUnread: countUnread && existing == null);
    if (atMe && message.senderId != selfUserId) {
      final conversation = _conversation(message.conversationId);
      final unreadMention = compareSeq(message.seqId, conversation.readSeq) > 0;
      if (unreadMention && activeConversationId != message.conversationId) {
        conversation.atMe = true;
        conversation.atMeCount += existing == null ? 1 : 0;
        conversation.atMeMessageId ??= message.messageId;
        if (atAll) {
          conversation.atAll = true;
          conversation.atAllCount += existing == null ? 1 : 0;
        }
        conversation.atPersonal = conversation.atPersonal || message.atUsers.contains(selfUserId);
      }
    }
  }

  void _applyEdit(Map<String, dynamic> payload, {required bool atMe, required bool atAll}) {
    final id = payload['message_id']?.toString();
    if (id == null) return;
    final existing = messages[id];
    final revision = payload['content_revision'] is num ? (payload['content_revision'] as num).toInt() : null;
    if (existing != null && revision != null && revision < existing.contentRevision) return;
    final message = _messageFromPayload(payload, atMe: atMe, atAll: atAll);
    if (existing != null) {
      message.deliveryState = mergeDeliveryState(existing.deliveryState, message.deliveryState);
      message.recalledPlaintext = existing.recalledPlaintext;
    }
    message.edited = payload['edited'] != false;
    messages[id] = message;
    for (final item in messages.values) {
      if (item.replyTo?.messageId == id && item.replyTo!.state != 'recalled') {
        item.replyTo!.state = 'edited';
      }
    }
    final conversation = conversations[message.conversationId];
    if (conversation != null && compareSeq(message.seqId, conversation.latestSeq) >= 0) {
      conversation.lastPreview = messagePreview(
        messageType: message.messageType,
        content: message.content,
        encrypted: message.isEncrypted,
        senderName: message.senderName,
        own: message.senderId == selfUserId,
      );
      conversation.lastMessageType = message.messageType;
    }
  }

  void _applyRecall(Map<String, dynamic> payload) {
    final id = payload['message_id']?.toString();
    if (id == null) return;
    final existing = messages[id];
    final conversationId = payload['conversation_id']?.toString() ?? existing?.conversationId;
    if (conversationId == null) return;
    final recallUser = payload['recall_user_id']?.toString() ?? selfUserId;
    final operatorRole = payload['recall_operator_role']?.toString();
    final senderId = payload['sender_id']?.toString() ?? existing?.senderId ?? '';
    final record = existing ??
        MessageRecord(
          messageId: id,
          conversationId: conversationId,
          senderId: senderId,
          messageType: 'text',
          content: '',
          seqId: payload['seq_id']?.toString(),
        );
    if (!record.isRecalled &&
        record.messageType == 'text' &&
        record.senderId == selfUserId &&
        recallUser == selfUserId &&
        (operatorRole == null || operatorRole.isEmpty)) {
      record.recalledPlaintext = record.content;
    }
    record.isRecalled = true;
    record.content = '';
    record.atUsers = const [];
    record.replyTo = null;
    record.forwardedFrom = null;
    record.recallUserId = recallUser;
    record.recallOperatorRole = operatorRole;
    record.senderId = senderId.isEmpty ? record.senderId : senderId;
    messages[id] = record;
    _cascadeRecall(record);
    final conversation = _conversation(conversationId);
    if (compareSeq(record.seqId, conversation.latestSeq) >= 0 || conversation.latestSeq == '0') {
      conversation.lastRecalled = true;
      conversation.lastPreview = messagePreview(
        messageType: 'recalled',
        content: '',
        recalled: true,
        own: recallUser == selfUserId,
        senderName: record.senderName,
      );
      conversation.lastMessageType = 'recalled';
    }
    if (record.senderId != selfUserId && compareSeq(conversation.readSeq, record.seqId) < 0) {
      conversation.unreadCount = (conversation.unreadCount - 1).clamp(0, 1 << 30);
    }
    _clearMention(conversation, id);
  }

  void _cascadeRecall(MessageRecord recalled) {
    for (final item in messages.values) {
      if (item.replyTo?.messageId == recalled.messageId) {
        item.replyTo!.state = 'recalled';
        item.replyTo!.previewText = null;
      }
    }
  }

  void _applyOfflineSync(Map<String, dynamic> payload) {
    syncTruncated = payload['truncated'] == true;
    final conversationItems = payload['conversations'];
    if (conversationItems is List) {
      for (final item in conversationItems) {
        if (item is Map) _mergeConversationSnapshot(_map(item));
      }
    }
    final incoming = <Map<String, dynamic>>[];
    for (final key in ['messages', 'revisions']) {
      final list = payload[key];
      if (list is List) {
        for (final item in list) {
          if (item is Map) incoming.add(_map(item));
        }
      }
    }
    for (final item in incoming) {
      _upsertMessage(item, atMe: false, atAll: false, countUnread: false);
      final seq = item['seq_id']?.toString();
      final conversationId = item['conversation_id']?.toString();
      if (seq != null && conversationId != null) {
        final conversation = _conversation(conversationId);
        conversation.syncSeq = maxSeq(conversation.syncSeq, seq);
      }
    }
  }

  void _mergeConversationSnapshot(Map<String, dynamic> json) {
    final id = json['conversation_id']?.toString();
    if (id == null || id.isEmpty) return;
    final current = conversations[id];
    final next = ConversationRecord.fromJson(json);
    if (current != null) {
      next.syncSeq = maxSeq(current.syncSeq, next.latestSeq);
      next.myRole ??= current.myRole;
      if (json['draft_preview'] == null) next.draftPreview = current.draftPreview;
    } else {
      next.syncSeq = next.latestSeq;
    }
    if (json['last_message'] is Map) {
      final last = _map(json['last_message']);
      next.lastPreview = last['content_preview']?.toString() ?? next.lastPreview;
      next.lastMessageType = last['message_type']?.toString();
      next.lastSenderId = last['sender_id']?.toString();
      next.lastSenderName = last['sender_nickname']?.toString();
      next.lastRecalled = last['is_recalled'] == true;
    }
    conversations[id] = next;
  }

  void _applyDraft(Map<String, dynamic> payload) {
    final id = payload['conversation_id']?.toString();
    if (id == null) return;
    final cleared = payload['cleared'] == true;
    final content = payload['content']?.toString() ?? '';
    drafts[id] = DraftRecord(
      conversationId: id,
      content: cleared ? '' : content,
      replyToMessageId: payload['reply_to_message_id']?.toString(),
      replyPreview: payload['reply_to'] is Map ? _map(payload['reply_to'])['preview_text']?.toString() : null,
      atUsers: payload['at_users'] is List
          ? (payload['at_users'] as List).whereType<Map>().map(_map).toList()
          : const [],
      updatedAt: payload['updated_at'] is num ? (payload['updated_at'] as num).toInt() : 0,
      cleared: cleared,
    );
    _conversation(id).draftPreview = cleared ? null : (payload['draft_preview']?.toString() ?? draftPreviewOf(content));
  }

  void _applyPresence(Map<String, dynamic> payload) {
    final userId = payload['user_id']?.toString();
    if (userId == null) return;
    presence[userId] = PresenceRecord(
      userId: userId,
      status: payload['status']?.toString() ?? 'offline',
      lastSeenAt: payload['last_seen_at'] is num ? (payload['last_seen_at'] as num).toInt() : null,
      visible: payload['visible'] != false,
    );
  }

  void _applyAnnouncement(Map<String, dynamic> payload) {
    final groupId = payload['group_id']?.toString();
    final announcementId = payload['announcement_id']?.toString();
    if (groupId == null || announcementId == null) return;
    final action = payload['action']?.toString() ?? 'published';
    final body = payload['announcement'] is Map ? _map(payload['announcement']) : const <String, dynamic>{};
    if (action == 'deleted') {
      announcements['$groupId:$announcementId'] = AnnouncementRecord(
        groupId: groupId,
        announcementId: announcementId,
        action: action,
        deleted: true,
        actorId: payload['actor_id']?.toString(),
      );
      return;
    }
    final actor = payload['actor_id']?.toString();
    announcements['$groupId:$announcementId'] = AnnouncementRecord(
      groupId: groupId,
      announcementId: announcementId,
      title: body['title']?.toString() ?? '',
      content: body['content']?.toString() ?? '',
      action: action,
      actorId: actor,
      readByMe: body['read_by_me'] == true || actor == selfUserId,
      pinned: body['pinned'] == true || action == 'published',
    );
  }

  void _applyAnnouncementRead(Map<String, dynamic> payload) {
    final groupId = payload['group_id']?.toString();
    final announcementId = payload['announcement_id']?.toString();
    if (groupId == null || announcementId == null) return;
    final current = announcements['$groupId:$announcementId'];
    if (current == null) return;
    if (payload['user_id']?.toString() == selfUserId) current.readByMe = true;
    if (payload['read_count'] is num) current.readCount = (payload['read_count'] as num).toInt();
    if (payload['unread_count'] is num) current.unreadCount = (payload['unread_count'] as num).toInt();
  }

  void _touchPreview(MessageRecord message, {required bool countUnread}) {
    final conversation = _conversation(message.conversationId);
    final newer = compareSeq(message.seqId, conversation.latestSeq) >= 0;
    if (message.seqId != null && message.seqId!.isNotEmpty) {
      conversation.latestSeq = maxSeq(conversation.latestSeq, message.seqId);
      conversation.syncSeq = maxSeq(conversation.syncSeq, message.seqId);
      conversation.updatedAt = message.timestampMs ?? conversation.updatedAt;
    }
    if (newer) {
      conversation.lastPreview = messagePreview(
        messageType: message.messageType,
        content: message.content,
        recalled: message.isRecalled,
        encrypted: message.isEncrypted,
        senderName: message.senderName,
        own: message.senderId == selfUserId,
      );
      conversation.lastMessageType = message.isRecalled ? 'recalled' : message.messageType;
      conversation.lastSenderId = message.senderId;
      conversation.lastSenderName = message.senderName;
      conversation.lastRecalled = message.isRecalled;
    }
    final visible = activeConversationId == message.conversationId;
    if (countUnread && !visible && message.senderId != selfUserId && !message.isRecalled) {
      conversation.unreadCount += 1;
    }
    if (message.conversationId.startsWith('grp_')) conversation.type = 'group';
    conversation.peerId ??= otherParticipant(message.conversationId, selfUserId);
  }

  ConversationRecord _conversation(String id) {
    return conversations.putIfAbsent(id, () => ConversationRecord(conversationId: id, type: id.startsWith('grp_') ? 'group' : 'private'));
  }

  MessageRecord _messageFromPayload(Map<String, dynamic> payload, {required bool atMe, required bool atAll}) {
    final content = payload['content']?.toString() ?? '';
    final encrypted = payload['is_encrypted'] == true || _looksEncrypted(content);
    final senderInfo = payload['sender_info'] is Map ? _map(payload['sender_info']) : const <String, dynamic>{};
    return MessageRecord(
      messageId: payload['message_id']?.toString() ?? payload['client_message_id']?.toString() ?? '',
      conversationId: payload['conversation_id']?.toString() ?? payload['group_id']?.toString() ?? '',
      senderId: payload['sender_id']?.toString() ?? '',
      messageType: payload['message_type']?.toString() ?? 'text',
      content: payload['is_recalled'] == true ? '' : content,
      clientMessageId: payload['client_message_id']?.toString(),
      seqId: payload['seq_id']?.toString(),
      deliveryState: payload['delivery_state']?.toString() ?? 'sent',
      timestampMs: _parseTime(payload['timestamp']) ?? (payload['timestamp_ms'] is num ? (payload['timestamp_ms'] as num).toInt() : null),
      senderName: senderInfo['nickname']?.toString() ?? payload['sender_name']?.toString(),
      replyTo: payload['reply_to'] is Map ? ReplySnapshot.fromJson(_map(payload['reply_to'])) : null,
      atUsers: _stringList(payload['at_users']),
      forwardedFrom: payload['forwarded_from'] is Map ? _map(payload['forwarded_from']) : null,
      edited: payload['edited'] == true,
      editedAt: payload['edited_at']?.toString(),
      contentRevision: payload['content_revision'] is num ? (payload['content_revision'] as num).toInt() : 1,
      isRecalled: payload['is_recalled'] == true || payload['status'] == 3,
      isEncrypted: encrypted,
      atMe: atMe,
      atAll: atAll,
    );
  }

  void _clearMentionsUpTo(ConversationRecord conversation, String seq) {
    final pending = messages.values.where((message) {
      return message.conversationId == conversation.conversationId &&
          message.atMe &&
          compareSeq(message.seqId, seq) > 0;
    });
    conversation.atMeCount = pending.length;
    conversation.atAllCount = pending.where((message) => message.atAll).length;
    conversation.atMe = conversation.atMeCount > 0;
    conversation.atAll = conversation.atAllCount > 0;
    conversation.atMeMessageId = pending.isEmpty ? null : pending.first.messageId;
    if (!conversation.atMe) conversation.atPersonal = false;
  }

  void _clearMention(ConversationRecord conversation, String messageId) {
    if (conversation.atMeMessageId == messageId) {
      conversation.atMeMessageId = null;
      conversation.atMeCount = (conversation.atMeCount - 1).clamp(0, 1 << 30);
      conversation.atMe = conversation.atMeCount > 0;
    }
  }
}

bool _looksEncrypted(String content) {
  try {
    final decoded = jsonDecode(content);
    return decoded is Map && decoded['ciphertext'] is String && decoded['iv'] is String;
  } catch (_) {
    return false;
  }
}

Map<String, dynamic> _payload(Map<String, dynamic> frame) {
  final message = frame['message'];
  if (message is Map) return _map(message);
  final copy = Map<String, dynamic>.from(frame)..remove('type');
  return copy;
}

Map<String, dynamic> _map(Object? value) {
  final source = value as Map;
  return source.map((key, item) => MapEntry(key.toString(), item));
}

List<String> _stringList(Object? value) {
  if (value is! List) return const [];
  return value.map((item) => item.toString()).where((item) => item.isNotEmpty).toList();
}

int? _parseTime(Object? value) {
  if (value is num) return value.toInt();
  if (value is String) {
    final parsed = DateTime.tryParse(value.replaceFirst(' ', 'T'));
    return parsed?.millisecondsSinceEpoch;
  }
  return null;
}

String? _senderName(Map<String, dynamic> json) {
  final info = json['sender_info'];
  if (info is Map) return info['nickname']?.toString();
  return null;
}

String _previewFromLast(Map<String, dynamic> json) {
  final last = json['last_message'];
  if (last is Map) return last['content_preview']?.toString() ?? '';
  return '';
}
