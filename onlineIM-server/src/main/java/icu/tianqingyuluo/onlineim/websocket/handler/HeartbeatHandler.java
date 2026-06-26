package icu.tianqingyuluo.onlineim.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 心跳控制帧处理器
 * 处理客户端发送的 HEARTBEAT 帧，刷新会话活跃时间并回复 HEARTBEAT_ACK
 * 不走 MessageTypeSenderRegistry，由 WebSocketMessageRouter 直接短路分发
 */
@Slf4j
@Component
public class HeartbeatHandler {

    public static final String TYPE_HEARTBEAT = "HEARTBEAT";
    public static final String TYPE_HEARTBEAT_ACK = "HEARTBEAT_ACK";

    private final ObjectMapper objectMapper;
    private final UserSessionService userSessionService;

    public HeartbeatHandler(ObjectMapper objectMapper,
                            UserSessionService userSessionService) {
        this.objectMapper = objectMapper;
        this.userSessionService = userSessionService;
    }

    /**
     * 处理心跳帧
     * @param session 发送心跳的会话
     */
    public void handle(WebSocketSession session) {
        if (session == null || !session.isActive()) {
            return;
        }

        // 刷新本地会话活跃时间
        session.refreshLastActiveAt();

        // 刷新 Redis 中的 activeTime
        try {
            userSessionService.refreshActiveTime(session.getUserId(), session.getDeviceId());
        } catch (Exception e) {
            log.warn("刷新Redis活跃时间失败: userId={}, deviceId={}, error={}",
                    session.getUserId(), session.getDeviceId(), e.getMessage());
        }

        // 回复 HEARTBEAT_ACK
        try {
            String ackJson = objectMapper.writeValueAsString(
                    Map.of(
                            "type", TYPE_HEARTBEAT_ACK,
                            "message", Map.of("serverTime", System.currentTimeMillis())
                    )
            );
            session.sendMessage(ackJson);
        } catch (Exception e) {
            log.error("发送HEARTBEAT_ACK失败: connectionId={}, error={}",
                    session.getConnectionId(), e.getMessage());
        }

        log.debug("心跳处理完成: connectionId={}, userId={}", session.getConnectionId(), session.getUserId());
    }
}
