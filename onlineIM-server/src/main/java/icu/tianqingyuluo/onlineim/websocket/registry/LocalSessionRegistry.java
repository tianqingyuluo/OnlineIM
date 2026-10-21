package icu.tianqingyuluo.onlineim.websocket.registry;

import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentMap;

/**
 * 本地WebSocket会话注册表
 * 管理当前服务实例上的所有WebSocket连接
 * 支持多层映射以便于快速查找会话
 */
@Slf4j
@Component
public class LocalSessionRegistry {

    /**
     * connectionId -> WebSocketSession 映射
     * connectionId为全局唯一ID，用于横向扩展时的路由
     */
    private final ConcurrentMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * socketId -> connectionId 映射
     * socketId是Vert.x WebSocket的内部标识
     */
    private final ConcurrentMap<String, String> socketIdToConnectionId = new ConcurrentHashMap<>();

    /**
     * userId -> connectionId 映射
     * 指向该用户最近登记的一条连接，供仍按“一个用户一条连接”投递消息的路径使用
     */
    private final ConcurrentMap<String, String> userIdToConnectionId = new ConcurrentHashMap<>();

    /**
     * userId -> 该用户在本节点上的全部 connectionId。
     * 多个标签页 / 设备各占一条；关掉其中一条不能把整个用户拆掉。
     */
    private final ConcurrentMap<String, Set<String>> userConnections = new ConcurrentHashMap<>();

    private final List<SessionRemovalListener> removalListeners = new CopyOnWriteArrayList<>();

    private final TaskScheduler taskScheduler;

    @Value("${websocket.heartbeat.interval:30s}")
    private Duration heartbeatInterval;

    @Value("${websocket.heartbeat.timeout:90s}")
    private Duration heartbeatTimeout;

    public LocalSessionRegistry(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void initScheduler() {
        taskScheduler.scheduleAtFixedRate(this::scheduledCleanup, heartbeatInterval);
        log.info("心跳超时清理调度器已启动: 间隔={}, 超时阈值={}", heartbeatInterval, heartbeatTimeout);
    }

    /**
     * 定时清理超时和失效会话
     */
    private void scheduledCleanup() {
        try {
            cleanupInactiveSessions(heartbeatTimeout.toMillis());
        } catch (Exception e) {
            log.error("定时清理会话异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 注册WebSocket会话
     * @param session WebSocket会话对象
     */
    public void register(WebSocketSession session) {
        if (session == null || session.getConnectionId() == null) {
            log.warn("无法注册空会话或无连接ID的会话");
            return;
        }

        String connectionId = session.getConnectionId();
        String socketId = session.getSocketId();
        String userId = session.getUserId();

        // 注册到各映射表
        sessionMap.put(connectionId, session);
        
        if (socketId != null) {
            socketIdToConnectionId.put(socketId, connectionId);
        }
        
        if (userId != null) {
            userConnections.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(connectionId);
            userIdToConnectionId.put(userId, connectionId);
        }

        log.info("WebSocket会话已注册: connectionId={}, userId={}", connectionId, userId);
    }

    public void addRemovalListener(SessionRemovalListener listener) {
        if (listener != null) {
            removalListeners.add(listener);
        }
    }

    /**
     * 通过connectionId注销会话
     * @param connectionId 连接ID
     */
    public void unregister(String connectionId) {
        WebSocketSession session = sessionMap.remove(connectionId);
        if (session != null) {
            detach(session, true);
            log.info("WebSocket会话已注销: connectionId={}, userId={}", connectionId, session.getUserId());
        }
    }

    /**
     * 通过socketId和userId注销会话
     * @param socketId WebSocket内部标识
     * @param userId 用户ID
     */
    public void unregisterBySocketId(String socketId, String userId) {
        String connectionId = socketIdToConnectionId.get(socketId);
        if (connectionId != null) {
            unregister(connectionId);
        } else if (userId != null) {
            // 备用方案：通过userId查找并注销
            String connId = userIdToConnectionId.get(userId);
            if (connId != null) {
                unregister(connId);
            }
        }
    }

    /**
     * 通过connectionId获取会话
     * @param connectionId 连接ID
     * @return WebSocket会话，如果不存在或已失效则返回null
     */
    public WebSocketSession getByConnectionId(String connectionId) {
        WebSocketSession session = sessionMap.get(connectionId);
        // 验证会话是否有效
        if (session != null && session.isActive()) {
            return session;
        }
        return null;
    }

    /**
     * 通过userId获取会话
     * @param userId 用户ID
     * @return WebSocket会话，如果不存在或已失效则返回null
     */
    public WebSocketSession getByUserId(String userId) {
        String connectionId = userIdToConnectionId.get(userId);
        if (connectionId != null) {
            WebSocketSession session = getByConnectionId(connectionId);
            if (session != null) {
                return session;
            }
        }
        for (WebSocketSession session : getSessionsByUserId(userId)) {
            return session;
        }
        return null;
    }

    /**
     * 该用户在本节点上的全部活跃连接。presence 推送要扇出到每个标签页。
     */
    public List<WebSocketSession> getSessionsByUserId(String userId) {
        Set<String> connectionIds = userId == null ? null : userConnections.get(userId);
        if (connectionIds == null || connectionIds.isEmpty()) {
            return List.of();
        }
        List<WebSocketSession> sessions = new ArrayList<>();
        for (String connectionId : connectionIds) {
            WebSocketSession session = getByConnectionId(connectionId);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    /**
     * 通过userId获取connectionId
     * @param userId 用户ID
     * @return 连接ID，如果不存在则返回null
     */
    public String getConnectionIdByUserId(String userId) {
        return userIdToConnectionId.get(userId);
    }

    /**
     * 检查用户列表中是否有用户在当前实例上有连接
     * @param userIds 用户ID列表
     * @return 是否有用户在线
     */
    public boolean hasUserOnline(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }
        return userIds.stream().anyMatch(userId -> getByUserId(userId) != null);
    }

    /**
     * 获取当前在线连接数
     * @return 连接数量
     */
    public int getOnlineCount() {
        return sessionMap.size();
    }

    /**
     * 清理所有失效的会话
     * 判据：socket 已关闭（isActive==false）或 心跳超时（lastActiveAt 超过阈值）
     */
    public void cleanupInactiveSessions() {
        cleanupInactiveSessions(heartbeatTimeout.toMillis());
    }

    /**
     * 清理所有失效的会话
     * @param timeoutMs 心跳超时阈值（毫秒）
     */
    public void cleanupInactiveSessions(long timeoutMs) {
        sessionMap.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            boolean inactive = !session.isActive();
            boolean heartbeatTimeout = session.isHeartbeatTimeout(timeoutMs);

            if (inactive || heartbeatTimeout) {
                if (heartbeatTimeout && !inactive) {
                    // 心跳超时但 socket 未关闭，主动 close
                    session.close();
                    log.info("心跳超时断连: connectionId={}, userId={}, lastActiveAt={}",
                            entry.getKey(), session.getUserId(), session.getLastActiveAt());
                }

                detach(session, true);
                log.info("清理失效会话: connectionId={}, userId={}", entry.getKey(), session.getUserId());
                return true;
            }
            return false;
        });
    }

    /**
     * 从用户索引里拿掉这一条连接。同一用户还有别的连接时，保留其中一条作为主连接。
     */
    private void detach(WebSocketSession session, boolean notify) {
        String connectionId = session.getConnectionId();
        String socketId = session.getSocketId();
        String userId = session.getUserId();
        if (socketId != null) {
            socketIdToConnectionId.remove(socketId, connectionId);
        }
        if (userId != null) {
            Set<String> connectionIds = userConnections.get(userId);
            if (connectionIds != null) {
                connectionIds.remove(connectionId);
                if (connectionIds.isEmpty()) {
                    userConnections.remove(userId, connectionIds);
                    userIdToConnectionId.remove(userId);
                } else if (connectionId.equals(userIdToConnectionId.get(userId))) {
                    userIdToConnectionId.put(userId, connectionIdOf(connectionIds));
                }
            } else if (connectionId.equals(userIdToConnectionId.get(userId))) {
                userIdToConnectionId.remove(userId);
            }
        }
        if (notify && userId != null && connectionId != null) {
            for (SessionRemovalListener listener : removalListeners) {
                try {
                    listener.onSessionRemoved(userId, connectionId);
                } catch (Exception e) {
                    log.warn("会话移除回调失败: userId={}, connectionId={}, error={}", userId, connectionId, e.getMessage());
                }
            }
        }
    }

    private static String connectionIdOf(Set<String> connectionIds) {
        for (String connectionId : connectionIds) {
            return connectionId;
        }
        return null;
    }
}
