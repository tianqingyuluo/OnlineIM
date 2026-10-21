package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.PresenceService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 PRESENCE_UPDATE 推给本节点上的观众。一个用户有多条连接时每条都发，多标签页才能同时更新。
 */
@Slf4j
@Component
public class PresenceUpdateReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;

    public PresenceUpdateReceiver(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String getSupportedMessageType() {
        return PresenceService.STREAM_TYPE;
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(event.getMessage());
            String userId = text(node, "user_id");
            String status = text(node, "status");
            if (userId == null || status == null) {
                log.error("在线状态通知缺少必要参数: {}", event.getMessage());
                return false;
            }
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("user_id", userId);
            message.put("status", status);
            message.put("last_seen_at", node.hasNonNull("last_seen_at") ? node.get("last_seen_at").asLong() : null);
            message.put("server_time", node.hasNonNull("server_time") ? node.get("server_time").asLong() : System.currentTimeMillis());

            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", PresenceService.STREAM_TYPE);
            frame.put("message", message);
            String json = objectMapper.writeValueAsString(frame);

            List<String> receivers = event.getReceiverIDs() == null ? List.of() : event.getReceiverIDs();
            for (String receiverId : receivers) {
                for (WebSocketSession session : sessionRegistry.getSessionsByUserId(receiverId)) {
                    session.sendMessage(json);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("投递在线状态通知异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
