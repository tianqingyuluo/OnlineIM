import '../protocol/frame_types.dart';

enum CallPhase { idle, outgoing, incoming, connecting, inCall, ended }

class CallSnapshot {
  const CallSnapshot({
    this.phase = CallPhase.idle,
    this.callId,
    this.callType = 'video',
    this.peerId,
    this.conversationId,
    this.muted = false,
    this.cameraEnabled = true,
    this.speakerOn = false,
    this.minimized = false,
    this.endReason,
    this.localSdp,
    this.remoteSdp,
    this.durationSeconds = 0,
    this.ringing = false,
  });

  final CallPhase phase;
  final String? callId;
  final String callType;
  final String? peerId;
  final String? conversationId;
  final bool muted;
  final bool cameraEnabled;
  final bool speakerOn;
  final bool minimized;
  final String? endReason;
  final String? localSdp;
  final String? remoteSdp;
  final int durationSeconds;
  final bool ringing;

  bool get active => phase == CallPhase.outgoing || phase == CallPhase.incoming || phase == CallPhase.connecting || phase == CallPhase.inCall;

  CallSnapshot copyWith({
    CallPhase? phase,
    String? callId,
    String? callType,
    String? peerId,
    String? conversationId,
    bool? muted,
    bool? cameraEnabled,
    bool? speakerOn,
    bool? minimized,
    String? endReason,
    String? localSdp,
    String? remoteSdp,
    int? durationSeconds,
    bool? ringing,
    bool clearEnd = false,
  }) {
    return CallSnapshot(
      phase: phase ?? this.phase,
      callId: callId ?? this.callId,
      callType: callType ?? this.callType,
      peerId: peerId ?? this.peerId,
      conversationId: conversationId ?? this.conversationId,
      muted: muted ?? this.muted,
      cameraEnabled: cameraEnabled ?? this.cameraEnabled,
      speakerOn: speakerOn ?? this.speakerOn,
      minimized: minimized ?? this.minimized,
      endReason: clearEnd ? null : (endReason ?? this.endReason),
      localSdp: localSdp ?? this.localSdp,
      remoteSdp: remoteSdp ?? this.remoteSdp,
      durationSeconds: durationSeconds ?? this.durationSeconds,
      ringing: ringing ?? this.ringing,
    );
  }
}

class IceCandidate {
  IceCandidate({required this.candidate, this.sdpMid, this.sdpMLineIndex});

  final String candidate;
  final String? sdpMid;
  final int? sdpMLineIndex;
}

/// 1v1 通话状态机。媒体面由 [CallMedia] 实现，信令帧与服务端登记表一致。
class CallController {
  CallSnapshot state = const CallSnapshot();
  final List<IceCandidate> pendingLocalCandidates = [];
  final List<Map<String, dynamic>> outbound = [];
  final List<IceCandidate> pendingRemoteCandidates = [];
  var _remoteDescriptionSet = false;

  void invite({
    required String callId,
    required String targetId,
    required String conversationId,
    required String callType,
    required String sdp,
  }) {
    if (state.active) {
      throw StateError('同一用户同时只能有一路通话');
    }
    _remoteDescriptionSet = false;
    pendingRemoteCandidates.clear();
    state = CallSnapshot(
      phase: CallPhase.outgoing,
      callId: callId,
      callType: callType,
      peerId: targetId,
      conversationId: conversationId,
      localSdp: sdp,
      cameraEnabled: callType != 'audio',
      ringing: true,
    );
    _send(frameCallInvite, {
      'call_id': callId,
      'target_id': targetId,
      'conversation_id': conversationId,
      'call_type': callType,
      'sdp': sdp,
    });
  }

  void applySignal(Map<String, dynamic> frame) {
    final type = canonicalFrameType(frame['type']?.toString() ?? '');
    final message = frame['message'] is Map
        ? (frame['message'] as Map).map((key, value) => MapEntry(key.toString(), value))
        : frame;
    final callId = message['call_id']?.toString() ?? state.callId;
    switch (type) {
      case frameCallInvite:
      case frameCallOffer:
        if (state.active && state.callId != callId) {
          _send(frameCallBusy, {'call_id': callId, 'call_status': 'busy'});
          return;
        }
        _remoteDescriptionSet = false;
        pendingRemoteCandidates.clear();
        state = CallSnapshot(
          phase: CallPhase.incoming,
          callId: callId,
          callType: message['call_type']?.toString() ?? 'video',
          peerId: message['caller_id']?.toString() ?? message['target_id']?.toString(),
          conversationId: message['conversation_id']?.toString(),
          remoteSdp: message['sdp']?.toString(),
          cameraEnabled: message['call_type'] != 'audio',
          ringing: true,
        );
        _send(frameCallRinging, {'call_id': callId});
      case frameCallRinging:
        if (state.phase == CallPhase.outgoing && state.callId == callId) {
          state = state.copyWith(ringing: true);
        }
      case frameCallAnswer:
        if (state.callId != callId) return;
        state = state.copyWith(phase: CallPhase.inCall, remoteSdp: message['sdp']?.toString(), ringing: false, clearEnd: true);
        _remoteDescriptionSet = true;
        _flushRemoteCandidates();
      case frameCallCandidate:
        final candidate = message['candidate']?.toString();
        if (candidate == null) return;
        final ice = IceCandidate(
          candidate: candidate,
          sdpMid: message['sdp_mid']?.toString(),
          sdpMLineIndex: message['sdp_m_line_index'] is num ? (message['sdp_m_line_index'] as num).toInt() : null,
        );
        if (!_remoteDescriptionSet) {
          pendingRemoteCandidates.add(ice);
        }
      case frameCallReject:
        _finish(callId, 'rejected');
      case frameCallCancel:
        _finish(callId, message['reason']?.toString() ?? 'cancelled');
      case frameCallBusy:
        _finish(callId, 'busy');
      case frameCallEnd:
      case frameCallHangup:
        final duration = message['duration_seconds'];
        _finish(callId, 'completed', duration: duration is num ? duration.toInt() : state.durationSeconds);
      case frameCallHandledElsewhere:
        if (state.callId == callId && (state.phase == CallPhase.incoming || state.ringing)) {
          _finish(callId, message['call_status']?.toString() ?? 'handled_elsewhere');
        }
      default:
        break;
    }
  }

  void answer(String sdp) {
    if (state.phase != CallPhase.incoming || state.callId == null) return;
    state = state.copyWith(phase: CallPhase.inCall, localSdp: sdp, ringing: false);
    _send(frameCallAnswer, {'call_id': state.callId, 'sdp': sdp});
    _remoteDescriptionSet = true;
    _flushRemoteCandidates();
  }

  void reject() {
    if (state.callId == null) return;
    _send(frameCallReject, {'call_id': state.callId});
    _finish(state.callId, 'rejected');
  }

  void cancel() {
    if (state.callId == null) return;
    _send(frameCallCancel, {'call_id': state.callId, 'reason': 'user'});
    _finish(state.callId, 'cancelled');
  }

  void hangup() {
    if (state.callId == null) return;
    _send(frameCallEnd, {'call_id': state.callId});
    _finish(state.callId, 'completed');
  }

  void addLocalCandidate(IceCandidate candidate) {
    if (state.callId == null) {
      pendingLocalCandidates.add(candidate);
      return;
    }
    _send(frameCallCandidate, {
      'call_id': state.callId,
      'candidate': candidate.candidate,
      if (candidate.sdpMid != null) 'sdp_mid': candidate.sdpMid,
      if (candidate.sdpMLineIndex != null) 'sdp_m_line_index': candidate.sdpMLineIndex,
    });
  }

  void toggleMute() => state = state.copyWith(muted: !state.muted);
  void toggleCamera() => state = state.copyWith(cameraEnabled: !state.cameraEnabled);
  void toggleSpeaker() => state = state.copyWith(speakerOn: !state.speakerOn);
  void minimize() => state = state.copyWith(minimized: true);
  void restore() => state = state.copyWith(minimized: false);

  void reset() {
    state = const CallSnapshot();
    pendingLocalCandidates.clear();
    pendingRemoteCandidates.clear();
    _remoteDescriptionSet = false;
  }

  void _flushRemoteCandidates() {
    pendingRemoteCandidates.clear();
  }

  void _finish(String? callId, String reason, {int? duration}) {
    if (callId != null && state.callId != null && state.callId != callId && state.phase != CallPhase.idle) {
      return;
    }
    state = state.copyWith(
      phase: CallPhase.ended,
      ringing: false,
      endReason: reason,
      durationSeconds: duration ?? state.durationSeconds,
      minimized: false,
    );
  }

  void _send(String type, Map<String, dynamic> message) {
    outbound.add({'type': type, 'message': message});
  }
}

/// 媒体引擎抽象。正式构建使用 WebRTC，测试使用内存实现。
abstract class CallMedia {
  Future<String> createOffer({required String callType});
  Future<String> createAnswer(String remoteSdp, {required String callType});
  Future<void> setRemote(String sdp);
  Future<void> addCandidate(IceCandidate candidate);
  Future<void> setMuted(bool muted);
  Future<void> setCameraEnabled(bool enabled);
  Future<void> setSpeaker(bool speakerOn);
  Future<void> dispose();
}

class MemoryCallMedia implements CallMedia {
  String? local;
  String? remote;
  bool muted = false;
  bool camera = true;
  bool speaker = false;
  final candidates = <IceCandidate>[];

  @override
  Future<String> createOffer({required String callType}) async {
    local = 'offer-$callType';
    return local!;
  }

  @override
  Future<String> createAnswer(String remoteSdp, {required String callType}) async {
    remote = remoteSdp;
    local = 'answer-$callType';
    return local!;
  }

  @override
  Future<void> setRemote(String sdp) async => remote = sdp;

  @override
  Future<void> addCandidate(IceCandidate candidate) async => candidates.add(candidate);

  @override
  Future<void> setMuted(bool value) async => muted = value;

  @override
  Future<void> setCameraEnabled(bool enabled) async => camera = enabled;

  @override
  Future<void> setSpeaker(bool speakerOn) async => speaker = speakerOn;

  @override
  Future<void> dispose() async {}
}
