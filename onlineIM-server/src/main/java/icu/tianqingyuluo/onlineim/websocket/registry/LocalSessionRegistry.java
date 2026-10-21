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
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 本地 WebSocket 会话注册表。
 * 一个用户可以同时有多条连接。同一 deviceId 的新连接会替换旧连接（关闭码 4000），其他设备不受影响。
 * 每摘掉一条连接都会通知监听器，在线状态只在该用户再没有连接时才变为离线。
 */
@Slf4j
@Component
public class LocalSessionRegistry {

    /**
     * connectionId -> WebSocketSession
     */
    private final ConcurrentMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    /**
     * socketId -> connectionId
     */
    private final ConcurrentMap<String, String> socketIdToConnectionId = new ConcurrentHashMap<>();

    /**
     * userId -> connectionId
     * 指向该用户最近登记的一条连接，供仍按“一个用户一条连接”查询的路径使用
     */
    private final ConcurrentMap<String, String> userIdToConnectionId = new ConcurrentHashMap<>();

    /**
     * userId -> 该用户在本节点上的全部 connectionId
     */
    private final ConcurrentMap<String, Set<String>> userConnections = new ConcurrentHashMap<>();

    /**
     * userId -> (deviceId -> 该设备当前的 connectionId)
     * 同一设备只留最新的一条；不同设备可以同时在线。
     */
    private final ConcurrentMap<String, ConcurrentMap<String, Set<String>>> userDevices = new ConcurrentHashMap<>();

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

    private void scheduledCleanup() {
        try {
            cleanupInactiveSessions(heartbeatTimeout.toMillis());
        } catch (Exception e) {
            log.error("定时清理会话异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 注册 WebSocket 会话。同一 deviceId 的旧连接会被替换并关闭（关闭码 4000），不注销账号。
     */
    public void register(WebSocketSession session) {
        if (session == null || session.getConnectionId() == null) {
            log.warn("无法注册空会话或无连接ID的会话");
            return;
        }

        String connectionId = session.getConnectionId();
        String socketId = session.getSocketId();
        String userId = session.getUserId();

        sessionMap.put(connectionId, session);

        if (socketId != null) {
            socketIdToConnectionId.put(socketId, connectionId);
        }

        if (userId != null) {
            userConnections.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(connectionId);
            userIdToConnectionId.put(userId, connectionId);
            String deviceKey = deviceKey(session);
            Set<String> deviceConnections = userDevices
                    .computeIfAbsent(userId, key -> new ConcurrentHashMap<>())
                    .computeIfAbsent(deviceKey, key -> ConcurrentHashMap.newKeySet());
            List<String> replaced = new ArrayList<>();
            for (String existing : deviceConnections) {
                if (!existing.equals(connectionId)) {
                    replaced.add(existing);
                }
            }
            deviceConnections.add(connectionId);
            for (String previous : replaced) {
                deviceConnections.remove(previous);
                replacePreviousConnection(previous);
            }
        }

        log.info("WebSocket会话已注册: connectionId={}, userId={}, deviceId={}",
                connectionId, userId, session.getDeviceId());
    }

    private void replacePreviousConnection(String previousConnectionId) {
        WebSocketSession previous = sessionMap.remove(previousConnectionId);
        if (previous == null) {
            return;
        }
        previous.close((short) 4000, "replaced");
        detach(previous, true);
        log.info("同设备旧连接已替换: connectionId={}, userId={}", previousConnectionId, previous.getUserId());
    }

    public void addRemovalListener(SessionRemovalListener listener) {
        if (listener != null) {
            removalListeners.add(listener);
        }
    }

    /**
     * 通过 connectionId 注销会话。只移除这条连接，并通知在线状态。
     */
    public void unregister(String connectionId) {
        WebSocketSession session = sessionMap.remove(connectionId);
        if (session != null) {
            detach(session, true);
            log.info("WebSocket会话已注销: connectionId={}, userId={}", connectionId, session.getUserId());
        }
    }

    /**
     * 通过 socketId 注销会话。找不到这条连接时不会清掉该用户的其他设备。
     */
    public void unregisterBySocketId(String socketId, String userId) {
        String connectionId = socketId == null ? null : socketIdToConnectionId.get(socketId);
        if (connectionId != null) {
            unregister(connectionId);
            return;
        }
        if (userId == null || socketId == null) {
            return;
        }
        for (WebSocketSession session : getSessionsByUserId(userId)) {
            if (socketId.equals(session.getSocketId())) {
                unregister(session.getConnectionId());
                return;
            }
        }
    }

    public WebSocketSession getByConnectionId(String connectionId) {
        WebSocketSession session = sessionMap.get(connectionId);
        if (session != null && session.isActive()) {
            return session;
        }
        return null;
    }

    /**
     * 通过 userId 获取任意一条仍有效的会话。
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
     * 该用户在本节点上的全部活跃连接。在线状态、草稿和消息都要扇出到每一条。
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
     * 与 {@link #getSessionsByUserId} 相同。没有连接时返回空列表，不返回 null。
     */
    public List<WebSocketSession> getAllByUserId(String userId) {
        return getSessionsByUserId(userId);
    }

    /**
     * 向指定设备的全部连接发送挤下线帧并关闭（关闭码 4001）。
     * 其他设备的连接保留，在线状态只在一条都不剩时才变为离线。
     */
    public boolean kickDevice(String userId, String deviceId, String frameJson) {
        List<WebSocketSession> targets = sessionsOfDevice(userId, deviceId);
        if (targets.isEmpty()) {
            return false;
        }
        for (WebSocketSession session : targets) {
            if (session.isActive()) {
                session.sendThenClose(frameJson, (short) 4001, "kicked");
            }
            unregister(session.getConnectionId());
        }
        return true;
    }

    /**
     * 安静关闭某设备的全部连接（主动登出）。不发送挤下线文案。
     */
    public void closeDevice(String userId, String deviceId) {
        for (WebSocketSession session : sessionsOfDevice(userId, deviceId)) {
            unregister(session.getConnectionId());
            session.close();
        }
    }

    public WebSocketSession findByDevice(String userId, String deviceId) {
        for (WebSocketSession session : sessionsOfDevice(userId, deviceId)) {
            if (session.isActive()) {
                return session;
            }
        }
        return null;
    }

    public String getConnectionIdByUserId(String userId) {
        WebSocketSession session = getByUserId(userId);
        return session == null ? null : session.getConnectionId();
    }

    public boolean hasUserOnline(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }
        return userIds.stream().anyMatch(userId -> getByUserId(userId) != null);
    }

    public int getOnlineCount() {
        return sessionMap.size();
    }

    public void cleanupInactiveSessions() {
        cleanupInactiveSessions(heartbeatTimeout.toMillis());
    }

    /**
     * 清理失效会话。只摘掉超时的那条连接，保留同一用户的其他设备，并通知在线状态。
     */
    public void cleanupInactiveSessions(long timeoutMs) {
        sessionMap.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            boolean inactive = !session.isActive();
            boolean heartbeatTimeout = session.isHeartbeatTimeout(timeoutMs);

            if (inactive || heartbeatTimeout) {
                if (heartbeatTimeout && !inactive) {
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

    private List<WebSocketSession> sessionsOfDevice(String userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return List.of();
        }
        ConcurrentMap<String, Set<String>> devices = userDevices.get(userId);
        if (devices == null) {
            return List.of();
        }
        Set<String> connectionIds = devices.get(deviceId);
        if (connectionIds == null || connectionIds.isEmpty()) {
            return List.of();
        }
        List<WebSocketSession> sessions = new ArrayList<>();
        for (String connectionId : new ArrayList<>(connectionIds)) {
            WebSocketSession session = sessionMap.get(connectionId);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    private static String deviceKey(WebSocketSession session) {
        if (session.getDeviceId() != null && !session.getDeviceId().isBlank()) {
            return session.getDeviceId();
        }
        return session.getConnectionId();
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
            ConcurrentMap<String, Set<String>> devices = userDevices.get(userId);
            if (devices != null) {
                Set<String> deviceConnections = devices.get(deviceKey(session));
                if (deviceConnections != null) {
                    deviceConnections.remove(connectionId);
                    if (deviceConnections.isEmpty()) {
                        devices.remove(deviceKey(session), deviceConnections);
                    }
                }
                if (devices.isEmpty()) {
                    userDevices.remove(userId, devices);
                }
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
