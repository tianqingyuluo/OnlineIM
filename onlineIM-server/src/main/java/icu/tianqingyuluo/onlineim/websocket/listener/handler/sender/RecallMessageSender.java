package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.RecallMessageRequest;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 处理客户端的 RECALL_MESSAGE_REQUEST。
 * 与 REST DELETE /api/v1/messages/{messageId} 共用 MessageService#recallMessage 的校验
 * （只能撤回自己发送的消息、不能重复撤回、必须在撤回时间窗内），
 * 校验通过后由 MessageService 持久化撤回状态并经 Redis Stream 推送撤回通知。
 */
@Slf4j
@Component
public class RecallMessageSender implements MessageSenderHandler {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public RecallMessageSender(ObjectMapper objectMapper, MessageService messageService) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
    }

    @Override
    public String getSupportedMessageType() {
        return "RECALL_MESSAGE_REQUEST";
    }

    @Override
    public boolean publishMessage(WebSocketSession session, String message) {
        if (session == null || session.getUserId() == null) {
            return false;
        }
        RecallMessageRequest request;
        try {
            request = objectMapper.readValue(message, RecallMessageRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("撤回请求格式错误");
        }
        if (request.getMessageId() == null || request.getMessageId().isBlank()) {
            throw new IllegalArgumentException("message_id 不能为空");
        }
        // 操作者始终取自已认证的会话，忽略客户端自报的 operator_id / sender_id
        return messageService.recallMessage(request.getMessageId(), session.getUserId());
    }
}
