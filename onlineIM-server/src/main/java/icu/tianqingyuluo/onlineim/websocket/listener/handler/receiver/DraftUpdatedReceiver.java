package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.DraftService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把已保存的草稿推给该用户自己在本节点上的连接，用于另一台设备更新输入框和会话列表。
 */
@Slf4j
@Component
public class DraftUpdatedReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;

    public DraftUpdatedReceiver(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String getSupportedMessageType() {
        return DraftService.STREAM_TYPE;
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }
        try {
            String userId = event.getSenderID();
            WebSocketSession session = userId == null ? null : sessionRegistry.getByUserId(userId);
            if (session == null || !session.isActive()) {
                return true;
            }
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", DraftService.STREAM_TYPE);
            frame.put("message", objectMapper.readTree(event.getMessage()));
            session.sendMessage(objectMapper.writeValueAsString(frame));
            return true;
        } catch (Exception e) {
            log.error("投递草稿更新异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
