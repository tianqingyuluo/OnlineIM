package icu.tianqingyuluo.onlineim.websocket.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.MessageTypeSenderRegistry;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.sender.MessageSenderHandler;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket消息路由器
 * 负责解析和分发WebSocket消息到对应的处理器
 * 与框架解耦，不依赖特定的WebSocket实现
 */
@Slf4j
@Component
public class WebSocketMessageRouter {

    private final ObjectMapper objectMapper;
    private final MessageTypeSenderRegistry messageTypeSenderRegistry;
    private final HeartbeatHandler heartbeatHandler;

    public WebSocketMessageRouter(ObjectMapper objectMapper,
                                  MessageTypeSenderRegistry messageTypeSenderRegistry,
                                  HeartbeatHandler heartbeatHandler) {
        this.objectMapper = objectMapper;
        this.messageTypeSenderRegistry = messageTypeSenderRegistry;
        this.heartbeatHandler = heartbeatHandler;
    }

    /**
     * 路由并处理WebSocket消息
     * @param session WebSocket会话
     * @param messageText 接收到的消息文本
     */
    public void route(WebSocketSession session, String messageText) {
        if (session == null || !session.isActive()) {
            log.warn("无法处理消息: 会话无效");
            return;
        }

        // 任意入站帧均刷新会话活跃时间（保活）
        session.refreshLastActiveAt();

        String userId = session.getUserId();
        String connectionId = session.getConnectionId();

        log.info("收到消息: userId={}, connectionId={}, message={}", userId, connectionId, messageText);

        try {
            // 解析JSON消息
            JsonNode jsonNode = objectMapper.readTree(messageText);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "UNKNOWN";
            String message = jsonNode.has("message") ? jsonNode.get("message").toString() : "";

            log.info("解析消息: userId={}, type={}, content={}", userId, type, message);

            // 控制帧短路：HEARTBEAT 不走业务 registry
            if (HeartbeatHandler.TYPE_HEARTBEAT.equals(type)) {
                heartbeatHandler.handle(session);
                return;
            }

            // 根据消息类型分发到对应的处理器
            if (messageTypeSenderRegistry.supportMessageType(type)) {
                MessageSenderHandler handler = messageTypeSenderRegistry.getHandler(type);
                boolean result = handler.publishMessage(message);
                
                if (!result) {
                    log.warn("消息处理失败: type={}, userId={}", type, userId);
                    sendError(session, "消息处理失败");
                }
            } else {
                log.warn("不支持的消息类型: type={}, userId={}", type, userId);
                sendError(session, "不支持的消息类型: " + type);
            }

        } catch (JsonProcessingException e) {
            log.error("解析WebSocket消息失败: userId={}, error={}", userId, e.getMessage());
            sendError(session, "消息格式错误");
        } catch (Exception e) {
            log.error("处理WebSocket消息异常: userId={}, error={}", userId, e.getMessage(), e);
            sendError(session, "服务器内部错误");
        }
    }

    /**
     * 发送错误消息给客户端
     * @param session WebSocket会话
     * @param errorMessage 错误信息
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            String errorJson = objectMapper.writeValueAsString(
                    new ErrorResponse("ERROR", errorMessage)
            );
            session.sendMessage(errorJson);
        } catch (Exception e) {
            log.error("发送错误消息失败: {}", e.getMessage());
        }
    }

    /**
     * 错误响应类
     */
    private record ErrorResponse(String type, String message) {}
}
