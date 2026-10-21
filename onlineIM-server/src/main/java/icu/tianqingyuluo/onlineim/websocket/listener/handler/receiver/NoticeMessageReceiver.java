package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.registry.SessionDelivery;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NoticeMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;

    public NoticeMessageReceiver(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String getSupportedMessageType() {
        return "NOTICE_MESSAGE";
    }

    @Override
    @SneakyThrows
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }

        List<String> receiverIds = event.getReceiverIDs();
        String messageScope = event.getMessage();

        log.info("返回提示信息给{}", receiverIds);

        for (String receiverId : receiverIds) {
            List<WebSocketSession> sessions = SessionDelivery.of(sessionRegistry, receiverId);
            if (sessions.isEmpty()) {
                continue;
            }
            Map<String, String> response = new HashMap<>();
            response.put("type", "NOTICE_MESSAGE_RESPONSE");
            response.put("message", messageScope);
            String responseJson = objectMapper.writeValueAsString(response);
            for (WebSocketSession receiverSession : sessions) {
                receiverSession.sendMessage(responseJson);
            }
            log.debug("已经向 {} 的 {} 台设备发送更新提醒", receiverId, sessions.size());
        }
        return true;
    }
}
