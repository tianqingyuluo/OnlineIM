import 'package:flutter/material.dart';

Future<void> showSafetyNumberSheet(
  BuildContext context, {
  required String digits,
  required bool verified,
  required VoidCallback onVerify,
}) {
  return showModalBottomSheet<void>(
    context: context,
    builder: (context) => Padding(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('安全码', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        const Text('请与对方当面核对这 60 位数字。每 5 位一组。'),
        const SizedBox(height: 12),
        SelectableText(digits, key: const Key('safety-number'), style: const TextStyle(letterSpacing: 0.5)),
        const SizedBox(height: 12),
        FilledButton(
          key: const Key('safety-verify'),
          onPressed: verified
              ? null
              : () {
                  onVerify();
                  Navigator.pop(context);
                },
          child: Text(verified ? '已验证' : '标记为已验证'),
        ),
      ]),
    ),
  );
}
