import 'package:flutter_test/flutter_test.dart';
import 'package:onlineim_flutter/protocol/conversation_id.dart';
import 'package:onlineim_flutter/protocol/frame_types.dart';
import 'package:onlineim_flutter/protocol/preview_text.dart';
import 'package:onlineim_flutter/protocol/protocol_engine.dart';
import 'package:onlineim_flutter/protocol/seq.dart';

void main() {
  test('compares sequence ids numerically and merges delivery without downgrade', () {
    expect(compareSeq('10', '9'), greaterThan(0));
    expect(compareSeq('99', '100'), lessThan(0));
    expect(mergeDeliveryState('read', 'sent'), 'read');
    expect(mergeDeliveryState('sending', 'sent'), 'sent');
    expect(privateConversationId('usr_b', 'usr_a'), 'conv_usr_ausr_b');
    expect(otherParticipant('conv_usr_ausr_b', 'usr_a'), 'usr_b');
  });

  test('aliases legacy frame names onto the frozen registry types', () {
    expect(canonicalFrameType('PING'), frameHeartbeat);
    expect(canonicalFrameType('PONG'), frameHeartbeatAck);
    expect(canonicalFrameType('HEARTBEAT_PONG'), frameHeartbeatAck);
    expect(canonicalFrameType('DELIVERED_ACK'), frameReceipt);
    expect(canonicalFrameType('READ_SYNC'), frameReadReceipt);
    expect(canonicalFrameType('PRESENCE_EVENT'), framePresence);
    expect(canonicalFrameType('GROUP_ANNOUNCEMENT_EVENT'), frameAnnouncement);
    expect(canonicalFrameType('MESSAGE_RECEIVE'), framePrivateResponse);
    expect(canonicalFrameType('EDIT_MESSAGE_RESPONSE'), frameMessageEdited);
    expect(reconnectDelayMs(0), 1000);
    expect(reconnectDelayMs(8), 30000);
  });

  test('applies ack, delivery, read, edit, recall and quote cascade', () {
    final engine = ProtocolEngine(selfUserId: 'usr_me');
    final local = MessageRecord(
      messageId: 'local',
      conversationId: 'conv_usr_meusr_peer',
      senderId: 'usr_me',
      messageType: 'text',
      content: '你好',
      clientMessageId: 'c1',
      deliveryState: 'sending',
    );
    engine.addLocalMessage(local);
    engine.applyFrame({
      'type': 'MESSAGE_ACK',
      'message': {
        'client_message_id': 'c1',
        'message_id': 'msg_1',
        'conversation_id': 'conv_usr_meusr_peer',
        'seq_id': '10',
        'delivery_state': 'sent',
      },
    });
    expect(engine.messages['msg_1']?.deliveryState, 'sent');
    engine.applyFrame({
      'type': 'READ_RECEIPT',
      'message': {'conversation_id': 'conv_usr_meusr_peer', 'reader_id': 'usr_peer', 'read_seq': '10'},
    });
    expect(engine.messages['msg_1']?.deliveryState, 'read');
    engine.applyFrame({
      'type': 'MESSAGE_ACK',
      'message': {'client_message_id': 'c1', 'message_id': 'msg_1', 'conversation_id': 'conv_usr_meusr_peer', 'seq_id': '10', 'delivery_state': 'sent'},
    });
    expect(engine.messages['msg_1']?.deliveryState, 'read');

    engine.applyFrame({
      'type': 'PRIVATE_MESSAGE_RESPONSE',
      'message': {
        'message_id': 'msg_2',
        'conversation_id': 'conv_usr_meusr_peer',
        'sender_id': 'usr_peer',
        'message_type': 'text',
        'content': '原消息',
        'seq_id': '11',
        'reply_to': {'message_id': 'msg_1', 'preview_text': '你好', 'state': 'active'},
      },
    });
    engine.applyFrame({
      'type': 'MESSAGE_EDITED',
      'message': {
        'message_id': 'msg_1',
        'conversation_id': 'conv_usr_meusr_peer',
        'sender_id': 'usr_me',
        'message_type': 'text',
        'content': '改过',
        'seq_id': '10',
        'edited': true,
        'content_revision': 2,
      },
    });
    expect(engine.messages['msg_1']?.edited, isTrue);
    expect(engine.messages['msg_2']?.replyTo?.state, 'edited');
    expect(engine.messages['msg_2']?.replyTo?.previewText, '你好');

    engine.applyFrame({
      'type': 'MESSAGE_RECALLED',
      'message_id': 'msg_1',
      'conversation_id': 'conv_usr_meusr_peer',
      'recall_user_id': 'usr_me',
      'sender_id': 'usr_me',
      'seq_id': '10',
    });
    expect(engine.messages['msg_1']?.isRecalled, isTrue);
    expect(engine.messages['msg_1']?.recalledPlaintext, '改过');
    expect(engine.messages['msg_1']?.canReedit, isTrue);
    expect(engine.messages['msg_2']?.replyTo?.state, 'recalled');
    expect(engine.messages['msg_2']?.replyTo?.previewText, isNull);
  });

  test('group admin recall does not keep re-edit text and mention badges survive', () {
    final engine = ProtocolEngine(selfUserId: 'usr_me');
    engine.activeConversationId = 'other';
    engine.applyFrame({
      'type': 'GROUP_MESSAGE_RESPONSE',
      'at_me': true,
      'at_all': true,
      'message': {
        'message_id': 'msg_g',
        'conversation_id': 'grp_1',
        'sender_id': 'usr_other',
        'message_type': 'text',
        'content': '@所有人',
        'seq_id': '5',
        'at_users': ['all', 'usr_me'],
      },
    });
    expect(engine.conversations['grp_1']?.atMe, isTrue);
    expect(engine.conversations['grp_1']?.atAll, isTrue);
    expect(engine.conversations['grp_1']?.unreadCount, 1);
    engine.applyFrame({
      'type': 'MESSAGE_RECALLED',
      'message_id': 'msg_g',
      'conversation_id': 'grp_1',
      'sender_id': 'usr_other',
      'recall_user_id': 'usr_admin',
      'recall_operator_role': 'admin',
      'seq_id': '5',
    });
    expect(engine.messages['msg_g']?.canReedit, isFalse);
    expect(engine.messages['msg_g']?.content, isEmpty);
  });

  test('offline sync uses authoritative unread and does not double count', () {
    final engine = ProtocolEngine(selfUserId: 'usr_me');
    engine.conversations['conv_1'] = ConversationRecord(conversationId: 'conv_1', unreadCount: 9, syncSeq: '3');
    engine.applyFrame({
      'type': 'OFFLINE_SYNC',
      'message': {
        'truncated': true,
        'conversations': [
          {'conversation_id': 'conv_1', 'type': 'private', 'name': '小明', 'unread_count': 1, 'latest_seq': '8', 'draft_preview': '写了一半'},
        ],
        'messages': [
          {'message_id': 'msg_8', 'conversation_id': 'conv_1', 'sender_id': 'usr_peer', 'message_type': 'text', 'content': '补齐', 'seq_id': '8'},
        ],
        'revisions': [
          {'message_id': 'msg_2', 'conversation_id': 'conv_1', 'sender_id': 'usr_peer', 'message_type': 'text', 'content': '', 'seq_id': '2', 'is_recalled': true, 'content_revision': 2},
        ],
      },
    });
    expect(engine.syncTruncated, isTrue);
    expect(engine.conversations['conv_1']?.unreadCount, 1);
    expect(engine.conversations['conv_1']?.draftPreview, '写了一半');
    expect(engine.messages['msg_8']?.content, '补齐');
    expect(engine.messages['msg_2']?.isRecalled, isTrue);
    expect(engine.conversations['conv_1']?.syncSeq, '8');
    final request = engine.buildSyncRequest();
    expect(request['type'], frameSyncRequest);
    expect((request['message'] as Map)['conversations'], isNotEmpty);
  });

  test('session kick, presence, draft, announcement and call frames', () {
    final engine = ProtocolEngine(selfUserId: 'usr_me');
    engine.applyFrame({
      'type': 'SESSION_KICKED',
      'message': {'reason': 'SAME_DEVICE_TYPE_LOGIN', 'message': '您的账号已在其他手机端登录，当前设备已下线', 'device_type': 'phone'},
    });
    expect(engine.sessionCleared, isTrue);
    expect(engine.stopReconnect, isTrue);
    expect(engine.kickReason, 'SAME_DEVICE_TYPE_LOGIN');

    engine.applyFrame({'type': 'PRESENCE_EVENT', 'message': {'user_id': 'usr_peer', 'status': 'online', 'last_seen_at': null}});
    expect(engine.presence['usr_peer']?.online, isTrue);
    engine.applyFrame({
      'type': 'DRAFT_UPDATED',
      'message': {'conversation_id': 'conv_1', 'content': '草稿', 'draft_preview': '草稿', 'updated_at': 5, 'cleared': false},
    });
    expect(engine.drafts['conv_1']?.content, '草稿');
    engine.applyFrame({
      'type': 'GROUP_ANNOUNCEMENT_EVENT',
      'message': {
        'action': 'published',
        'group_id': 'grp_1',
        'announcement_id': 'ann_1',
        'actor_id': 'usr_admin',
        'announcement': {'title': '开会', 'content': '三点', 'read_by_me': false},
      },
    });
    expect(engine.announcements['grp_1:ann_1']?.title, '开会');
    engine.applyFrame({
      'type': 'E2EE_STATE_CHANGED',
      'message': {'conversation_id': 'conv_1', 'enabled': true, 'updated_by': 'usr_me'},
    });
    expect(engine.conversations['conv_1']?.e2eeEnabled, isTrue);
    engine.applyFrame({'type': 'CALL_HANDLED_ELSEWHERE', 'message': {'call_id': 'call_1', 'call_status': 'answered'}});
    expect(engine.callSignals.single['type'], frameCallHandledElsewhere);
    engine.applyFrame({'type': 'CONNECT_RESPONSE', 'message': {}});
    expect(engine.connectedHandshake, isTrue);
  });

  test('preview, draft and permission windows', () {
    expect(draftPreviewOf('  你好   世界  '), '你好 世界');
    expect(messagePreview(messageType: 'image', content: '{}'), '[图片]');
    expect(messagePreview(messageType: 'call', content: '{"call_type":"video","call_status":"completed","duration_seconds":83}'), '[视频通话] 01:23');
    expect(quoteStateLabel('recalled', '原文'), '原消息已撤回');
    final now = DateTime.utc(2026, 10, 26, 8);
    expect(canRecall(own: true, group: false, myRole: null, sentAt: now.subtract(const Duration(seconds: 30)), now: now), isTrue);
    expect(canRecall(own: false, group: true, myRole: 'admin', sentAt: now.subtract(const Duration(seconds: 30)), now: now), isTrue);
    expect(canRecall(own: false, group: true, myRole: 'member', sentAt: now.subtract(const Duration(seconds: 30)), now: now), isFalse);
    expect(canRecall(own: true, group: false, myRole: null, sentAt: now.subtract(const Duration(minutes: 3)), now: now), isFalse);
    expect(canEdit(own: true, messageType: 'text', sentAt: now, now: now), isTrue);
    expect(canEdit(own: true, messageType: 'image', sentAt: now, now: now), isFalse);
    expect(mentionMeBadge, '[有人@我]');
    expect(editedBadge, '已编辑');
  });
}
