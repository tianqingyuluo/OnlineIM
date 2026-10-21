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
     * 设备类型：web / desktop / phone。同类型只保留一个连接。
     */
    private final String deviceType;

    /**
     * JWT令牌，用于会话验证
     */
    private final String token;

    /**
     * Vert.x WebSocket连接对象
     */
    private final ServerWebSocket socket;

    /**
     * 最后活跃时间（epoch millis）
     * 任意入站帧（含心跳）均刷新此值，用于服务端超时检测
     */
    @Getter(lombok.AccessLevel.NONE)
    @Builder.Default
    private volatile long lastActiveAt = System.currentTimeMillis();

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
        closeSocket(null, null);
    }

    /**
     * 带关闭码关闭。4000 表示同设备连接被新页面替换，4001 表示会话被挤下线。
     */
    public void close(short code, String reason) {
        closeSocket(code, reason);
    }

    /**
     * 先把帧写出去，写完再关闭，避免挤下线通知被直接掐掉。
     */
    public void sendThenClose(String message, short code, String reason) {
        if (socket == null || socket.isClosed()) {
            return;
        }
        try {
            socket.writeTextMessage(message, ignored -> close(code, reason));
        } catch (Exception e) {
            log.error("发送挤下线通知失败: connectionId={}, error={}", connectionId, e.getMessage(), e);
            close(code, reason);
        }
    }

    private void closeSocket(Short code, String reason) {
        if (socket != null && !socket.isClosed()) {
            try {
                if (code == null) {
                    socket.close();
                } else {
                    socket.close(code, reason == null ? "" : reason);
                }
                log.info("WebSocket连接已关闭: connectionId={}, userId={}, code={}", connectionId, userId, code);
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

    /**
     * 获取最后活跃时间
     * @return epoch millis
     */
    public long getLastActiveAt() {
        return lastActiveAt;
    }

    /**
     * 刷新最后活跃时间为当前时间
     */
    public void refreshLastActiveAt() {
        // volatile long 写入是原子的（Java 5+）
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * 判断会话是否因心跳超时而失活
     * @param timeoutMs 超时阈值（毫秒）
     * @return true 表示已超时
     */
    public boolean isHeartbeatTimeout(long timeoutMs) {
        return (System.currentTimeMillis() - lastActiveAt) > timeoutMs;
    }
}
