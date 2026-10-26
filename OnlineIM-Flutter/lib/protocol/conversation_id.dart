/// 私聊会话 ID：`conv_` + 两个用户 ID 按字典序直接拼接。
String privateConversationId(String firstUserId, String secondUserId) {
  if (firstUserId.trim().isEmpty || secondUserId.trim().isEmpty) {
    throw ArgumentError('用户ID不能为空');
  }
  final ordered = firstUserId.compareTo(secondUserId) <= 0
      ? '$firstUserId$secondUserId'
      : '$secondUserId$firstUserId';
  return 'conv_$ordered';
}

/// 从 `conv_` + 两个 `usr_` 用户 ID 中取出对方。格式不符时返回 null。
String? otherParticipant(String conversationId, String userId) {
  if (!conversationId.startsWith('conv_')) return null;
  final parts = conversationId.substring('conv_'.length).split('usr_');
  if (parts.length != 3 || parts[1].isEmpty || parts[2].isEmpty) return null;
  final first = 'usr_${parts[1]}';
  final second = 'usr_${parts[2]}';
  if (userId == first) return second;
  if (userId == second) return first;
  return null;
}
