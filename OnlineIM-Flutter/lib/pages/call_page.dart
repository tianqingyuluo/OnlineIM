import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../services/call_controller.dart';
import '../state/providers.dart';

class CallPage extends ConsumerWidget {
  const CallPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(imSessionProvider);
    final state = session.calls.state;
    final title = state.callType == 'audio' ? '语音通话' : '视频通话';
    final status = switch (state.phase) {
      CallPhase.outgoing => '正在呼叫…',
      CallPhase.incoming => '邀请你通话',
      CallPhase.connecting => '正在连接',
      CallPhase.inCall => '通话中',
      CallPhase.ended => _ended(state.endReason),
      CallPhase.idle => '空闲',
    };
    return Scaffold(
      backgroundColor: const Color(0xFF10231E),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        title: Text(title),
        actions: [
          IconButton(
            key: const Key('call-minimize'),
            onPressed: () {
              session.calls.minimize();
              session.refresh();
              context.pop();
            },
            icon: const Icon(Icons.picture_in_picture_alt),
          ),
        ],
      ),
      body: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.account_circle, size: 96, color: Colors.white70),
          const SizedBox(height: 12),
          Text(state.peerId ?? '对方', style: const TextStyle(color: Colors.white, fontSize: 22)),
          const SizedBox(height: 8),
          Text(status, key: const Key('call-status'), style: const TextStyle(color: Colors.white70)),
          const Spacer(),
          Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
            if (state.phase == CallPhase.incoming) ...[
              _round(key: const Key('call-reject'), icon: Icons.call_end, color: Colors.red, onTap: () {
                session.rejectCall();
                context.pop();
              }),
              _round(key: const Key('call-accept'), icon: Icons.call, color: Colors.green, onTap: session.answerCall),
            ] else ...[
              _round(
                key: const Key('call-mute'),
                icon: state.muted ? Icons.mic_off : Icons.mic,
                color: Colors.white24,
                onTap: () {
                  session.calls.toggleMute();
                  session.media.setMuted(session.calls.state.muted);
                  session.refresh();
                },
              ),
              _round(
                key: const Key('call-camera'),
                icon: state.cameraEnabled ? Icons.videocam : Icons.videocam_off,
                color: Colors.white24,
                onTap: () {
                  session.calls.toggleCamera();
                  session.media.setCameraEnabled(session.calls.state.cameraEnabled);
                  session.refresh();
                },
              ),
              _round(
                key: const Key('call-speaker'),
                icon: state.speakerOn ? Icons.volume_up : Icons.hearing,
                color: Colors.white24,
                onTap: () {
                  session.calls.toggleSpeaker();
                  session.media.setSpeaker(session.calls.state.speakerOn);
                  session.refresh();
                },
              ),
              _round(key: const Key('call-hangup'), icon: Icons.call_end, color: Colors.red, onTap: () {
                session.hangupCall();
                context.pop();
              }),
            ],
          ]),
          const SizedBox(height: 48),
        ],
      ),
    );
  }

  String _ended(String? reason) {
    switch (reason) {
      case 'busy':
        return '对方忙线';
      case 'rejected':
        return '已拒绝';
      case 'timeout':
        return '无人接听';
      case 'handled_elsewhere':
      case 'answered':
        return '已在其他设备处理';
      default:
        return '通话已结束';
    }
  }

  Widget _round({required Key key, required IconData icon, required Color color, required VoidCallback onTap}) {
    return FloatingActionButton(key: key, heroTag: key, backgroundColor: color, onPressed: onTap, child: Icon(icon));
  }
}
