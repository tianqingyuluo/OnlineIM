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
 * 把已保存的草稿推给该用户自己在本节点上的连接，用于其他标签页和设备更新输入框和会话列表。
 * 在线状态之后一个用户可以有多条连接，每一条都要收到。
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
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", DraftService.STREAM_TYPE);
            frame.put("message", objectMapper.readTree(event.getMessage()));
            String json = objectMapper.writeValueAsString(frame);
            for (WebSocketSession session : sessionRegistry.getSessionsByUserId(userId)) {
                session.sendMessage(json);
            }
            return true;
        } catch (Exception e) {
            log.error("投递草稿更新异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
