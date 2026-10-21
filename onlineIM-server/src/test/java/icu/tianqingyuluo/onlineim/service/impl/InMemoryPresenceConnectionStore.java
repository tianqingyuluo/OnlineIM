package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.service.PresenceConnectionStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 与 {@link RedisPresenceConnectionStore} 的 Lua 返回值对齐，供不依赖 Redis 的生命周期测试使用。
 */
public class InMemoryPresenceConnectionStore implements PresenceConnectionStore {

    private static final class Conn {
        final String userId;
        long expireAt;

        Conn(String userId, long expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }
    }

    private final Map<String, Conn> connections = new HashMap<>();
    private final Map<String, Set<String>> userConnections = new HashMap<>();
    private final Set<String> online = new HashSet<>();
    private final Map<String, Long> lastSeen = new HashMap<>();
    private final Set<String> hidden = new HashSet<>();
    private long lockUntil;

    @Override
    public synchronized int connect(String userId, String connectionId, long nowMs, long ttlMs) {
        int before = prune(userId, nowMs);
        connections.put(connectionId, new Conn(userId, nowMs + ttlMs));
        userConnections.computeIfAbsent(userId, key -> new LinkedHashSet<>()).add(connectionId);
        online.add(userId);
        return before;
    }

    @Override
    public synchronized int disconnect(String userId, String connectionId, long nowMs) {
        Conn existing = connections.get(connectionId);
        boolean existed = existing != null && existing.expireAt > nowMs;
        connections.remove(connectionId);
        Set<String> ids = userConnections.get(userId);
        if (ids != null) {
            ids.remove(connectionId);
        }
        int after = prune(userId, nowMs);
        if (after > 0) {
            return existed ? DISCONNECT_STILL_ONLINE : DISCONNECT_UNCHANGED;
        }
        if (online.remove(userId)) {
            lastSeen.put(userId, nowMs);
            return DISCONNECT_WENT_OFFLINE;
        }
        return DISCONNECT_UNCHANGED;
    }

    @Override
    public synchronized boolean heartbeat(String userId, String connectionId, long nowMs, long ttlMs) {
        Conn existing = connections.get(connectionId);
        if (existing != null && existing.expireAt > nowMs) {
            existing.expireAt = nowMs + ttlMs;
            return true;
        }
        return false;
    }

    @Override
    public synchronized int aliveCount(String userId, long nowMs) {
        return prune(userId, nowMs);
    }

    @Override
    public synchronized Long getLastSeen(String userId) {
        return lastSeen.get(userId);
    }

    @Override
    public synchronized void setHidden(String userId, boolean hidden) {
        if (hidden) {
            this.hidden.add(userId);
        } else {
            this.hidden.remove(userId);
        }
    }

    @Override
    public synchronized boolean isHidden(String userId) {
        return hidden.contains(userId);
    }

    @Override
    public synchronized Set<String> onlineUserIds() {
        return Set.copyOf(online);
    }

    @Override
    public synchronized boolean sweepOffline(String userId, long nowMs) {
        if (prune(userId, nowMs) > 0) {
            return false;
        }
        if (online.remove(userId)) {
            lastSeen.put(userId, nowMs);
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean trySweepLock(String owner, long ttlMs) {
        long now = System.currentTimeMillis();
        if (lockUntil > now) {
            return false;
        }
        lockUntil = now + ttlMs;
        return true;
    }

    private int prune(String userId, long nowMs) {
        Set<String> ids = userConnections.get(userId);
        if (ids == null) {
            return 0;
        }
        int alive = 0;
        Iterator<String> iterator = ids.iterator();
        while (iterator.hasNext()) {
            String connectionId = iterator.next();
            Conn connection = connections.get(connectionId);
            if (connection == null || connection.expireAt <= nowMs) {
                connections.remove(connectionId);
                iterator.remove();
            } else {
                alive++;
            }
        }
        if (ids.isEmpty()) {
            userConnections.remove(userId);
        }
        return alive;
    }
}
