import 'package:flutter/material.dart';

import '../crypto/e2ee_crypto.dart';
import '../protocol/preview_text.dart';
import '../protocol/protocol_engine.dart';

class MessageBubble extends StatelessWidget {
  const MessageBubble({super.key, required this.message, required this.mine, this.onLongPress, this.onQuoteTap, this.onRedial});

  final MessageRecord message;
  final bool mine;
  final VoidCallback? onLongPress;
  final VoidCallback? onQuoteTap;
  final VoidCallback? onRedial;

  @override
  Widget build(BuildContext context) {
    if (message.isRecalled) {
      final label = message.recallOperatorRole == 'owner'
          ? '群主撤回了一条消息'
          : message.recallOperatorRole == 'admin'
              ? '管理员撤回了一条消息'
              : mine
                  ? '你撤回了一条消息'
                  : '${message.senderName ?? '对方'} 撤回了一条消息';
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Center(
          child: Text(
            message.canReedit ? '$label  重新编辑' : label,
            key: Key('recalled-${message.messageId}'),
            style: const TextStyle(color: Colors.grey),
          ),
        ),
      );
    }
    final quote = message.replyTo;
    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: GestureDetector(
        key: Key('message-${message.messageId}'),
        onLongPress: onLongPress,
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 4, horizontal: 12),
          padding: const EdgeInsets.all(10),
          constraints: const BoxConstraints(maxWidth: 320),
          decoration: BoxDecoration(
            color: mine ? const Color(0xFFD8F3DC) : Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: const Color(0xFFE4E4E4)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (message.forwardedFrom != null)
                Text('转发自 ${message.forwardedFrom!['display_name'] ?? ''}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
              if (quote != null)
                GestureDetector(
                  onTap: onQuoteTap,
                  child: Container(
                    key: Key('quote-${message.messageId}'),
                    width: double.infinity,
                    margin: const EdgeInsets.only(bottom: 6),
                    padding: const EdgeInsets.all(6),
                    color: const Color(0xFFF4F4F4),
                    child: Text(quoteStateLabel(quote.state, quote.previewText), style: const TextStyle(fontSize: 12)),
                  ),
                ),
              _body(),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (message.edited) const Text(editedBadge, key: Key('edited-badge'), style: TextStyle(fontSize: 11, color: Colors.grey)),
                  const SizedBox(width: 6),
                  Text(_stateLabel(message.deliveryState), style: const TextStyle(fontSize: 11, color: Colors.grey)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _body() {
    if (message.isEncrypted && (message.localPlaintext == null || message.localPlaintext!.isEmpty)) {
      return const Text(lockedMessageText, key: Key('e2ee-locked'));
    }
    final text = message.localPlaintext ?? message.content;
    switch (message.messageType) {
      case 'image':
        return const Text('[图片] 点击查看');
      case 'file':
        return Text('[文件] $text');
      case 'voice':
      case 'audio':
        return const Text('[语音] 点击播放');
      case 'emoji':
      case 'sticker':
        return const Text('[表情]');
      case 'call':
        return TextButton(onPressed: onRedial, child: Text(callPreview(text)));
      default:
        return Text(text);
    }
  }

  String _stateLabel(String state) {
    switch (state) {
      case 'sending':
        return '发送中';
      case 'failed':
        return '失败';
      case 'delivered':
        return '已送达';
      case 'read':
        return '已读';
      default:
        return '';
    }
  }
}
