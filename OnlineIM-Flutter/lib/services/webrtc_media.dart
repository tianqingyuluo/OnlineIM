import 'package:flutter_webrtc/flutter_webrtc.dart';

import 'call_controller.dart';

/// 正式通话媒体面。单元测试使用 [MemoryCallMedia]，避免拉起原生 PeerConnection。
class WebRtcCallMedia implements CallMedia {
  RTCPeerConnection? _connection;
  MediaStream? _local;

  Future<void> _ensure(String callType, List<Map<String, dynamic>> iceServers) async {
    _connection ??= await createPeerConnection({'iceServers': iceServers});
    _local ??= await navigator.mediaDevices.getUserMedia({
      'audio': true,
      'video': callType == 'video',
    });
    for (final track in _local!.getTracks()) {
      await _connection!.addTrack(track, _local!);
    }
  }

  @override
  Future<String> createOffer({required String callType}) async {
    await _ensure(callType, const []);
    final offer = await _connection!.createOffer();
    await _connection!.setLocalDescription(offer);
    return offer.sdp ?? '';
  }

  @override
  Future<String> createAnswer(String remoteSdp, {required String callType}) async {
    await _ensure(callType, const []);
    await _connection!.setRemoteDescription(RTCSessionDescription(remoteSdp, 'offer'));
    final answer = await _connection!.createAnswer();
    await _connection!.setLocalDescription(answer);
    return answer.sdp ?? '';
  }

  @override
  Future<void> setRemote(String sdp) async {
    await _connection?.setRemoteDescription(RTCSessionDescription(sdp, 'answer'));
  }

  @override
  Future<void> addCandidate(IceCandidate candidate) async {
    await _connection?.addCandidate(RTCIceCandidate(candidate.candidate, candidate.sdpMid, candidate.sdpMLineIndex));
  }

  @override
  Future<void> setMuted(bool muted) async {
    for (final track in _local?.getAudioTracks() ?? <MediaStreamTrack>[]) {
      track.enabled = !muted;
    }
  }

  @override
  Future<void> setCameraEnabled(bool enabled) async {
    for (final track in _local?.getVideoTracks() ?? <MediaStreamTrack>[]) {
      track.enabled = enabled;
    }
  }

  @override
  Future<void> setSpeaker(bool speakerOn) => Helper.setSpeakerphoneOn(speakerOn);

  @override
  Future<void> dispose() async {
    for (final track in _local?.getTracks() ?? <MediaStreamTrack>[]) {
      await track.stop();
    }
    await _local?.dispose();
    await _connection?.close();
    _local = null;
    _connection = null;
  }
}
