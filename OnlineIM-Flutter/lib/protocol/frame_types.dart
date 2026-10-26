/// v1 冻结帧名见 `docs/ws-protocol-registry.md`。
/// 入站额外接受任务说明里的别名，出站始终使用登记表中的正式名称。
const frameHeartbeat = 'HEARTBEAT';
const frameHeartbeatAck = 'HEARTBEAT_ACK';
const frameError = 'ERROR';
const frameSyncRequest = 'SYNC_REQUEST';
const frameOfflineSync = 'OFFLINE_SYNC';
const framePrivateRequest = 'PRIVATE_MESSAGE_REQUEST';
const framePrivateResponse = 'PRIVATE_MESSAGE_RESPONSE';
const frameGroupRequest = 'GROUP_MESSAGE_REQUEST';
const frameGroupResponse = 'GROUP_MESSAGE_RESPONSE';
const frameMessageAck = 'MESSAGE_ACK';
const frameReceipt = 'RECEIPT';
const frameReadReceipt = 'READ_RECEIPT';
const frameRecallRequest = 'RECALL_MESSAGE_REQUEST';
const frameRecallResponse = 'RECALL_MESSAGE_RESPONSE';
const frameMessageRecalled = 'MESSAGE_RECALLED';
const frameEditRequest = 'EDIT_MESSAGE_REQUEST';
const frameMessageEdited = 'MESSAGE_EDITED';
const framePresence = 'PRESENCE_UPDATE';
const frameSessionKicked = 'SESSION_KICKED';
const frameDraftUpdated = 'DRAFT_UPDATED';
const frameE2ee = 'E2EE_STATE_CHANGED';
const frameAnnouncement = 'GROUP_ANNOUNCEMENT';
const frameAnnouncementRead = 'GROUP_ANNOUNCEMENT_READ';
const frameCallInvite = 'CALL_INVITE';
const frameCallOffer = 'CALL_OFFER';
const frameCallRinging = 'CALL_RINGING';
const frameCallAnswer = 'CALL_ANSWER';
const frameCallCandidate = 'CALL_CANDIDATE';
const frameCallReject = 'CALL_REJECT';
const frameCallCancel = 'CALL_CANCEL';
const frameCallBusy = 'CALL_BUSY';
const frameCallEnd = 'CALL_END';
const frameCallHangup = 'CALL_HANGUP';
const frameCallHandledElsewhere = 'CALL_HANDLED_ELSEWHERE';
const frameConnectResponse = 'CONNECT_RESPONSE';

const heartbeatInterval = Duration(seconds: 25);
const heartbeatAckMissThreshold = 2;
const recallWindow = Duration(seconds: 120);
const editWindow = Duration(seconds: 120);
const reconnectStepsMs = [1000, 2000, 4000, 8000, 16000, 30000];
const offlineReconnectThreshold = 5;

int reconnectDelayMs(int attempt, {double jitter = 0}) {
  final base = reconnectStepsMs[attempt.clamp(0, reconnectStepsMs.length - 1)];
  final delay = (base + base * 0.25 * jitter).round();
  return delay.clamp(0, 30000);
}

/// 入站别名 → 登记表正式类型。
String canonicalFrameType(String raw) {
  switch (raw) {
    case 'PING':
    case 'HEARTBEAT_PING':
      return frameHeartbeat;
    case 'PONG':
    case 'HEARTBEAT_PONG':
      return frameHeartbeatAck;
    case 'CLIENT_MESSAGE_SEND':
      return framePrivateRequest;
    case 'MESSAGE_RECEIVE':
      return framePrivateResponse;
    case 'DELIVERED_ACK':
      return frameReceipt;
    case 'READ_SYNC':
      return frameReadReceipt;
    case 'PRESENCE_EVENT':
      return framePresence;
    case 'GROUP_ANNOUNCEMENT_EVENT':
      return frameAnnouncement;
    case 'GROUP_ANNOUNCEMENT_READ_EVENT':
      return frameAnnouncementRead;
    case 'EDIT_MESSAGE_RESPONSE':
      return frameMessageEdited;
    default:
      return raw;
  }
}

Map<String, dynamic> wsFrame(String type, Map<String, dynamic> message) => {
      'type': type,
      'message': message,
    };
