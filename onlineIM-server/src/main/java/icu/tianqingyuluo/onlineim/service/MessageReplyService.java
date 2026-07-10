package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReplyReferenceResponse;

import java.util.List;

public interface MessageReplyService {
    MessageReplySnapshot createSnapshot(String conversationId, String targetMessageId, String userId);

    ReplyReferenceResponse resolveForResponse(String conversationId, MessageReplySnapshot snapshot);

    List<ReplyReferenceResponse> resolveBatchForResponses(
            String conversationId, List<MessageReplySnapshot> snapshots);

    void markTargetRecalled(String conversationId, String messageId);
}
