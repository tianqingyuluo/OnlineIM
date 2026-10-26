import 'dart:convert';

import 'frame_types.dart';

const mentionMeBadge = '[有人@我]';
const mentionAllBadge = '[@所有人]';
const draftBadge = '[草稿]';
const editedBadge = '已编辑';

String draftPreviewOf(String content) {
  final collapsed = content.replaceAll(RegExp(r'\s+'), ' ').trim();
  return _truncateCodePoints(collapsed, 40);
}

String replyPreviewOf(String content) => _truncateCodePoints(content.replaceAll(RegExp(r'\s+'), ' ').trim(), 80);

String _truncateCodePoints(String value, int maxPoints) {
  final points = value.runes.toList();
  if (points.length <= maxPoints) return value;
  return '${String.fromCharCodes(points.take(maxPoints))}…';
}

String messagePreview({
  required String messageType,
  required String content,
  bool recalled = false,
  bool encrypted = false,
  String? senderName,
  bool own = false,
}) {
  if (recalled) {
    if (own) return '你撤回了一条消息';
    if (senderName != null && senderName.isNotEmpty) return '$senderName 撤回了一条消息';
    return '[消息已撤回]';
  }
  if (encrypted || _looksEncrypted(content)) return '[加密消息]';
  switch (messageType) {
    case 'image':
      return '[图片]';
    case 'file':
      return '[文件]';
    case 'voice':
    case 'audio':
      return '[语音]';
    case 'emoji':
    case 'sticker':
      return '[表情]';
    case 'chat_record':
      return '[聊天记录]';
    case 'video':
      return '[视频]';
    case 'call':
      return callPreview(content);
    case 'recalled':
      return '[消息已撤回]';
    default:
      final text = content.replaceAll(RegExp(r'\s+'), ' ').trim();
      return _truncateCodePoints(text, 100);
  }
}

bool _looksEncrypted(String content) {
  final envelope = _tryMap(content);
  if (envelope == null) return false;
  return envelope['ciphertext'] is String && envelope['iv'] is String;
}

String callPreview(String content) {
  final payload = _tryMap(content) ?? const {};
  final type = payload['call_type'] == 'audio' ? '语音' : '视频';
  final status = payload['call_status']?.toString() ?? '';
  switch (status) {
    case 'completed':
      return '[$type通话] ${_formatDuration(payload['duration_seconds'])}';
    case 'missed':
      return '[未接$type通话]';
    case 'rejected':
      return '[已拒接]';
    case 'cancelled':
      return '[已取消]';
    case 'busy':
      return '[对方忙线]';
    default:
      return '[$type通话]';
  }
}

String quotePlaceholder(String messageType, {String? fileName, int? durationMs}) {
  switch (messageType) {
    case 'image':
      return fileName == null || fileName.isEmpty ? '[图片]' : '[图片] $fileName';
    case 'file':
      return fileName == null || fileName.isEmpty ? '[文件]' : '[文件] $fileName';
    case 'voice':
    case 'audio':
      if (durationMs == null || durationMs <= 0) return '[语音]';
      final seconds = (durationMs / 1000).round();
      final mm = (seconds ~/ 60).toString().padLeft(2, '0');
      final ss = (seconds % 60).toString().padLeft(2, '0');
      return '[语音 $mm:$ss]';
    case 'emoji':
    case 'sticker':
      return '[表情]';
    case 'chat_record':
      return '[聊天记录]';
    case 'video':
      return '[视频]';
    default:
      return replyPreviewOf(messageType);
  }
}

String quoteStateLabel(String? state, String? preview) {
  switch (state) {
    case 'recalled':
      return '原消息已撤回';
    case 'unavailable':
      return '原消息不可用';
    case 'edited':
      final text = preview ?? '';
      return text.isEmpty ? '原消息已编辑' : '$text · 原消息已编辑';
    default:
      return preview ?? '';
  }
}

String _formatDuration(Object? seconds) {
  final value = seconds is num ? seconds.round() : int.tryParse('$seconds') ?? 0;
  final mm = (value ~/ 60).toString().padLeft(2, '0');
  final ss = (value % 60).toString().padLeft(2, '0');
  return '$mm:$ss';
}

Map<String, dynamic>? _tryMap(String raw) {
  try {
    final decoded = jsonDecode(raw);
    if (decoded is Map) {
      return decoded.map((key, value) => MapEntry(key.toString(), value));
    }
  } catch (_) {}
  return null;
}

bool withinWindow(DateTime sentAt, Duration window, DateTime now) {
  if (window.inSeconds <= 0) return true;
  return now.difference(sentAt) <= window;
}

bool canRecall({
  required bool own,
  required bool group,
  required String? myRole,
  required DateTime sentAt,
  required DateTime now,
  bool alreadyRecalled = false,
}) {
  if (alreadyRecalled) return false;
  if (!withinWindow(sentAt, recallWindow, now)) return false;
  if (own) return true;
  if (!group) return false;
  return myRole == 'owner' || myRole == 'admin';
}

bool canEdit({
  required bool own,
  required String messageType,
  required DateTime sentAt,
  required DateTime now,
  bool recalled = false,
}) {
  if (!own || recalled || messageType != 'text') return false;
  return withinWindow(sentAt, editWindow, now);
}
