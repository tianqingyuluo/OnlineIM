package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.ConversationReadState;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageReadersResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReadStateResponse;

public interface ConversationReadStateService {

    ConversationReadState getOrCreate(String conversationId, String userId, String conversationType);

    boolean advanceDelivered(String conversationId, String userId, String conversationType, String deliveredSeq);

    boolean advanceRead(String conversationId, String userId, String conversationType, String readSeq);

    ReadStateResponse getReadState(String conversationId, String userId, String fromSeqId);

    MessageReadersResponse getMessageReaders(String conversationId, String messageId, String userId);
}
