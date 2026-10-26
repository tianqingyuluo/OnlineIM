import '../protocol/preview_text.dart';

class DraftInput {
  DraftInput({
    this.content = '',
    this.replyToMessageId,
    this.atUsers = const [],
    this.updatedAt = 0,
    this.dirty = false,
  });

  String content;
  String? replyToMessageId;
  List<Map<String, String>> atUsers;
  int updatedAt;
  bool dirty;

  String get preview => content.trim().isEmpty ? '' : draftPreviewOf(content);
}

/// 停止输入约 500ms 后保存；本地有更新的未保存输入时忽略更旧的远程草稿。
class DraftSync {
  DraftSync({this.debounce = const Duration(milliseconds: 500)});

  final Duration debounce;
  final Map<String, DraftInput> drafts = {};

  DraftInput of(String conversationId) => drafts.putIfAbsent(conversationId, DraftInput.new);

  bool noteLocalEdit(String conversationId, String content, int nowMs) {
    final draft = of(conversationId);
    if (draft.content == content && !draft.dirty) return false;
    draft.content = content;
    draft.updatedAt = nowMs;
    draft.dirty = true;
    return true;
  }

  bool due(String conversationId, int nowMs) {
    final draft = drafts[conversationId];
    if (draft == null || !draft.dirty) return false;
    return nowMs - draft.updatedAt >= debounce.inMilliseconds;
  }

  Map<String, dynamic>? bodyIfReady(String conversationId, int nowMs) {
    if (!due(conversationId, nowMs)) return null;
    final draft = drafts[conversationId]!;
    draft.dirty = false;
    final content = draft.content;
    if (content.trim().isEmpty) {
      return {'clear': true, 'conversation_id': conversationId};
    }
    return {
      'conversation_id': conversationId,
      'content': content,
      if (draft.replyToMessageId != null) 'reply_to_message_id': draft.replyToMessageId,
      'at_users': draft.atUsers,
    };
  }

  /// 远程草稿只在本地没有更新的未保存输入时覆盖。
  bool applyRemote({
    required String conversationId,
    required String? content,
    required int remoteUpdatedAt,
    required bool cleared,
    String? replyToMessageId,
  }) {
    final local = drafts[conversationId];
    if (local != null && local.dirty && local.updatedAt > remoteUpdatedAt) {
      return false;
    }
    drafts[conversationId] = DraftInput(
      content: cleared ? '' : (content ?? ''),
      replyToMessageId: cleared ? null : replyToMessageId,
      updatedAt: remoteUpdatedAt,
    );
    return true;
  }
}
