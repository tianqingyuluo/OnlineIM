package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.E2eeService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把私聊加密开关推给双方当前在线的每一台设备。
 */
@Slf4j
@Component
public class E2eeStateReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;

    public E2eeStateReceiver(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String getSupportedMessageType() {
        return E2eeService.STREAM_TYPE;
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }
        try {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", E2eeService.STREAM_TYPE);
            frame.put("message", objectMapper.readTree(event.getMessage()));
            String json = objectMapper.writeValueAsString(frame);
            List<String> receivers = event.getReceiverIDs() == null ? List.of() : event.getReceiverIDs();
            for (String receiverId : receivers) {
                for (WebSocketSession session : sessionRegistry.getSessionsByUserId(receiverId)) {
                    session.sendMessage(json);
                }
            }
            return true;
        } catch (Exception ex) {
            log.error("投递加密会话状态失败: {}", ex.getMessage(), ex);
            return false;
        }
    }
}
