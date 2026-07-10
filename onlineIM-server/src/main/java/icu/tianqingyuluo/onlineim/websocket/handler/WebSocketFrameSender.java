package icu.tianqingyuluo.onlineim.websocket.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一构造并发送 WebSocket 信封，避免各处理器重复定义协议格式。
 */
@Component
public class WebSocketFrameSender {

    private final ObjectMapper objectMapper;

    public WebSocketFrameSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean send(WebSocketSession session, String type, Object payload) {
        if (session == null || !session.isActive()) {
            return false;
        }
        try {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", type);
            frame.put("message", payload);
            return session.sendMessage(objectMapper.writeValueAsString(frame));
        } catch (JsonProcessingException ex) {
            return false;
        }
    }
}
