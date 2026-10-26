import 'package:flutter_test/flutter_test.dart';
import 'package:onlineim_flutter/protocol/frame_types.dart';
import 'package:onlineim_flutter/services/call_controller.dart';
import 'package:onlineim_flutter/services/draft_sync.dart';

void main() {
  test('outgoing call reaches in-call and ignores a second invite', () {
    final calls = CallController();
    calls.invite(callId: 'call_1', targetId: 'usr_b', conversationId: 'conv_usr_ausr_b', callType: 'video', sdp: 'offer');
    expect(calls.outbound.single['type'], frameCallInvite);
    calls.outbound.clear();
    calls.applySignal({'type': 'CALL_RINGING', 'message': {'call_id': 'call_1'}});
    calls.applySignal({'type': 'CALL_ANSWER', 'message': {'call_id': 'call_1', 'sdp': 'answer'}});
    expect(calls.state.phase, CallPhase.inCall);
    expect(calls.state.remoteSdp, 'answer');
    expect(() => calls.invite(callId: 'call_2', targetId: 'usr_c', conversationId: 'conv', callType: 'audio', sdp: 'x'), throwsStateError);
    calls.toggleMute();
    calls.toggleCamera();
    calls.toggleSpeaker();
    calls.minimize();
    expect(calls.state.muted, isTrue);
    expect(calls.state.cameraEnabled, isFalse);
    expect(calls.state.speakerOn, isTrue);
    expect(calls.state.minimized, isTrue);
    calls.hangup();
    expect(calls.state.phase, CallPhase.ended);
    expect(calls.state.endReason, 'completed');
    expect(calls.outbound.last['type'], frameCallEnd);
  });

  test('incoming call stops ringing when handled elsewhere', () {
    final calls = CallController();
    calls.applySignal({
      'type': 'CALL_INVITE',
      'message': {'call_id': 'call_9', 'caller_id': 'usr_a', 'conversation_id': 'conv', 'call_type': 'audio', 'sdp': 'offer'},
    });
    expect(calls.state.phase, CallPhase.incoming);
    expect(calls.state.ringing, isTrue);
    expect(calls.outbound.single['type'], frameCallRinging);
    calls.applySignal({'type': 'CALL_HANDLED_ELSEWHERE', 'message': {'call_id': 'call_9', 'call_status': 'answered'}});
    expect(calls.state.phase, CallPhase.ended);
    expect(calls.state.ringing, isFalse);
    expect(calls.state.endReason, 'answered');
  });

  test('busy, reject and cancel terminate the call', () {
    final calls = CallController();
    calls.invite(callId: 'call_b', targetId: 'usr_b', conversationId: 'conv', callType: 'video', sdp: 'offer');
    calls.applySignal({'type': 'CALL_BUSY', 'message': {'call_id': 'call_b', 'call_status': 'busy'}});
    expect(calls.state.endReason, 'busy');

    final incoming = CallController();
    incoming.applySignal({'type': 'CALL_OFFER', 'message': {'call_id': 'call_c', 'caller_id': 'usr_a', 'call_type': 'video', 'sdp': 'o'}});
    incoming.reject();
    expect(incoming.state.endReason, 'rejected');
    expect(incoming.outbound.last['type'], frameCallReject);
  });

  test('candidates queue until remote sdp exists and answer flushes them', () {
    final calls = CallController();
    calls.applySignal({'type': 'CALL_INVITE', 'message': {'call_id': 'call_d', 'caller_id': 'usr_a', 'call_type': 'video', 'sdp': 'offer'}});
    calls.applySignal({'type': 'CALL_CANDIDATE', 'message': {'call_id': 'call_d', 'candidate': 'cand', 'sdp_mid': '0', 'sdp_m_line_index': 0}});
    expect(calls.pendingRemoteCandidates, hasLength(1));
    calls.answer('answer-sdp');
    expect(calls.state.phase, CallPhase.inCall);
    expect(calls.pendingRemoteCandidates, isEmpty);
    expect(calls.outbound.last['type'], frameCallAnswer);
  });

  test('draft sync debounces and keeps a newer local edit', () {
    final sync = DraftSync(debounce: const Duration(milliseconds: 500));
    expect(sync.noteLocalEdit('conv', '写了一半', 1000), isTrue);
    expect(sync.bodyIfReady('conv', 1200), isNull);
    final body = sync.bodyIfReady('conv', 1600);
    expect(body?['content'], '写了一半');
    expect(sync.applyRemote(conversationId: 'conv', content: '旧', remoteUpdatedAt: 1500, cleared: false), isTrue);
    sync.noteLocalEdit('conv', '更新', 2000);
    expect(sync.applyRemote(conversationId: 'conv', content: '远程', remoteUpdatedAt: 1800, cleared: false), isFalse);
    expect(sync.of('conv').content, '更新');
    expect(sync.of('conv').preview, '更新');
  });
}
