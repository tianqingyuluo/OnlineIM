package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息撤回通知投递（RECALL_MESSAGE Stream 事件）。
 * 撤回的校验与持久化已在 MessageService#recallMessage 完成，这里只负责把通知推给本节点上在线的相关用户：
 * 撤回者收到 RECALL_MESSAGE_RESPONSE，其他接收者收到 MESSAGE_RECALLED。
 */
@Slf4j
@Component
public class RecallMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;

    @Autowired
    public RecallMessageReceiver(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String getSupportedMessageType() {
        return "RECALL_MESSAGE";
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(event.getMessage());
            String messageId = text(node, "message_id");
            String conversationId = text(node, "conversation_id");
            if (messageId == null || conversationId == null) {
                log.error("撤回通知缺少必要参数: {}", event.getMessage());
                return false;
            }
            String recallUserId = text(node, "recall_user_id") != null ? text(node, "recall_user_id") : event.getSenderID();
            long timestamp = node.hasNonNull("timestamp") ? node.get("timestamp").asLong() : System.currentTimeMillis();

            List<String> receivers = event.getReceiverIDs() == null ? List.of() : event.getReceiverIDs();
            for (String userId : receivers) {
                WebSocketSession session = sessionRegistry.getByUserId(userId);
                if (session == null || !session.isActive()) {
                    continue;
                }
                Map<String, Object> frame = new LinkedHashMap<>();
                boolean self = userId.equals(recallUserId);
                frame.put("type", self ? "RECALL_MESSAGE_RESPONSE" : "MESSAGE_RECALLED");
                if (self) {
                    frame.put("status", "success");
                }
                frame.put("message_id", messageId);
                frame.put("conversation_id", conversationId);
                frame.put("recall_user_id", recallUserId);
                frame.put("timestamp", timestamp);
                session.sendMessage(objectMapper.writeValueAsString(frame));
            }
            return true;
        } catch (Exception e) {
            log.error("投递消息撤回通知异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
