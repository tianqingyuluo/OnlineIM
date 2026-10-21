package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.service.MessageEditService;
import icu.tianqingyuluo.onlineim.util.MentionUtil;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.registry.SessionDelivery;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息编辑通知投递（EDIT_MESSAGE Stream 事件）。
 * 校验与持久化已在 MessageEditService 完成；这里读取最新的消息，给本节点上在线的每个会话成员
 * （含编辑者自己，用于多标签页同步）推送 MESSAGE_EDITED，message 与历史接口的消息格式相同。
 * 群聊额外带 at_me（编辑后的 @ 是否命中该接收者，含 @所有人）和 at_all。发送者本人的 at_me 为 false。
 */
@Slf4j
@Component
public class EditMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;
    private final MessageEditService messageEditService;

    public EditMessageReceiver(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry,
                               MessageEditService messageEditService) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.messageEditService = messageEditService;
    }

    @Override
    public String getSupportedMessageType() {
        return MessageEditService.STREAM_TYPE;
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(event.getMessage());
            String messageId = node.hasNonNull("message_id") ? node.get("message_id").asText() : null;
            String conversationId = node.hasNonNull("conversation_id") ? node.get("conversation_id").asText() : null;
            if (messageId == null || conversationId == null) {
                log.error("编辑通知缺少必要参数: {}", event.getMessage());
                return false;
            }
            List<String> receivers = event.getReceiverIDs() == null ? List.of() : event.getReceiverIDs();
            MessageResponse message = null;
            for (String userId : receivers) {
                List<WebSocketSession> sessions = SessionDelivery.of(sessionRegistry, userId);
                if (sessions.isEmpty()) {
                    continue;
                }
                if (message == null) {
                    message = messageEditService.loadResponse(conversationId, messageId);
                    if (message == null) {
                        return false;
                    }
                }
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("type", "MESSAGE_EDITED");
                frame.put("message", message);
                if (conversationId.startsWith("grp_")) {
                    String senderId = message.getSenderInfo() == null ? null : message.getSenderInfo().getUserId();
                    boolean mentioned = !userId.equals(senderId) && MentionUtil.mentions(message.getMentionedUserIds(), userId);
                    frame.put("at_me", mentioned);
                    frame.put("at_all", MentionUtil.mentionsAll(message.getMentionedUserIds()));
                }
                String json = objectMapper.writeValueAsString(frame);
                for (WebSocketSession session : sessions) {
                    session.sendMessage(json);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("投递消息编辑通知异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
