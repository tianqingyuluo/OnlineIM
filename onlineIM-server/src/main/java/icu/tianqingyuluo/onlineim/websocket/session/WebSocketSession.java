package icu.tianqingyuluo.onlineim.websocket.session;

import io.vertx.core.http.ServerWebSocket;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket会话封装类
 * 封装Vert.x ServerWebSocket，提供统一的会话管理接口
 * 用于横向扩展时的连接标识和消息发送
 */
@Slf4j
@Getter
@Builder
public class WebSocketSession {

    /**
     * 全局唯一的连接标识，用于横向扩展时的路由
     */
    private final String connectionId;

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * 设备ID，支持多端登录
     */
    private final String deviceId;

    /**
     * JWT令牌，用于会话验证
     */
    private final String token;

    /**
     * Vert.x WebSocket连接对象
     */
    private final ServerWebSocket socket;

    /**
     * 发送文本消息到客户端
     * @param message 要发送的消息内容
     * @return 是否发送成功
     */
    public boolean sendMessage(String message) {
        if (socket == null || socket.isClosed()) {
            log.warn("WebSocket连接已关闭，无法发送消息: connectionId={}", connectionId);
            return false;
        }
        try {
            socket.writeTextMessage(message);
            log.debug("消息发送成功: connectionId={}, userId={}", connectionId, userId);
            return true;
        } catch (Exception e) {
            log.error("发送消息失败: connectionId={}, error={}", connectionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 关闭WebSocket连接
     */
    public void close() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                log.info("WebSocket连接已关闭: connectionId={}, userId={}", connectionId, userId);
            } catch (Exception e) {
                log.error("关闭WebSocket连接异常: connectionId={}, error={}", connectionId, e.getMessage(), e);
            }
        }
    }

    /**
     * 检查会话是否有效（连接是否活跃）
     * @return 会话是否有效
     */
    public boolean isActive() {
        return socket != null && !socket.isClosed();
    }

    /**
     * 获取WebSocket的唯一标识（用于内部映射）
     * @return WebSocket的textHandlerID作为唯一标识
     */
    public String getSocketId() {
        return socket != null ? socket.textHandlerID() : null;
    }
}
