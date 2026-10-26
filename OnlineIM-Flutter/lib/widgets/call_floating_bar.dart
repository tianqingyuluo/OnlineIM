import 'package:flutter/material.dart';

import '../services/call_controller.dart';

class CallFloatingBar extends StatelessWidget {
  const CallFloatingBar({super.key, required this.state, required this.onRestore, required this.onHangup});

  final CallSnapshot state;
  final VoidCallback onRestore;
  final VoidCallback onHangup;

  @override
  Widget build(BuildContext context) {
    if (!state.minimized || !state.active) return const SizedBox.shrink();
    return Material(
      key: const Key('floating-call'),
      elevation: 6,
      borderRadius: BorderRadius.circular(24),
      color: const Color(0xFF1F8A70),
      child: InkWell(
        onTap: onRestore,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.call, color: Colors.white, size: 18),
            const SizedBox(width: 8),
            Text(state.phase == CallPhase.inCall ? '通话中' : '正在呼叫', style: const TextStyle(color: Colors.white)),
            IconButton(onPressed: onHangup, icon: const Icon(Icons.call_end, color: Colors.white)),
          ]),
        ),
      ),
    );
  }
}
