package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageEditRequest;
import icu.tianqingyuluo.onlineim.service.MessageEditService;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.springframework.stereotype.Component;

/**
 * 处理客户端的 EDIT_MESSAGE_REQUEST，与 REST PUT /api/v1/messages/{message_id} 共用 MessageEditService 的校验。
 * 成功后不单独回包：编辑者和其他成员一样收到 MESSAGE_EDITED；被拒绝时由路由层回 ERROR 帧（code 为编辑错误码）。
 */
@Component
public class EditMessageSender implements MessageSenderHandler {

    private final ObjectMapper objectMapper;
    private final MessageEditService messageEditService;

    public EditMessageSender(ObjectMapper objectMapper, MessageEditService messageEditService) {
        this.objectMapper = objectMapper;
        this.messageEditService = messageEditService;
    }

    @Override
    public String getSupportedMessageType() {
        return "EDIT_MESSAGE_REQUEST";
    }

    @Override
    public boolean publishMessage(WebSocketSession session, String message) {
        if (session == null || session.getUserId() == null) {
            return false;
        }
        MessageEditRequest request;
        try {
            request = objectMapper.readValue(message, MessageEditRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("编辑请求格式错误");
        }
        if (request.getMessageId() == null || request.getMessageId().isBlank()) {
            throw new IllegalArgumentException("message_id 不能为空");
        }
        // 编辑者始终取自已认证的会话
        messageEditService.edit(request.getMessageId(), request, session.getUserId());
        return true;
    }
}
