package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ServerIdentity;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceItemResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceQueryResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceSettingsResponse;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在线状态：多连接聚合、隐私、查询，以及经 Redis Stream 推给有权看见的人。
 */
@Slf4j
@Service
public class PresenceService {

    public static final String STREAM_TYPE = "PRESENCE_UPDATE";
    public static final String STATUS_ONLINE = "online";
    public static final String STATUS_OFFLINE = "offline";
    public static final int MAX_QUERY = 100;
    private static final int PUBLISH_CHUNK = 200;
    private static final long SWEEP_LOCK_MS = 25_000;

    private final PresenceConnectionStore store;
    private final PresenceAudienceService audience;
    private final RedisStreamService redisStreamService;
    private final ObjectMapper objectMapper;
    private final ServerIdentity serverIdentity;
    private final Duration connectionTtl;
    private final java.time.Clock clock;
    private final long sweepLockMs;

    /** 有两个构造器，Spring 需要明确用这一个，否则启动时报 “No default constructor found”。 */
    @Autowired
    public PresenceService(PresenceConnectionStore store,
                           PresenceAudienceService audience,
                           RedisStreamService redisStreamService,
                           ObjectMapper objectMapper,
                           LocalSessionRegistry sessionRegistry,
                           ServerIdentity serverIdentity,
                           @Value("${websocket.heartbeat.timeout:90s}") Duration connectionTtl) {
        this(store, audience, redisStreamService, objectMapper, serverIdentity, connectionTtl, java.time.Clock.systemUTC(), SWEEP_LOCK_MS);
        sessionRegistry.addRemovalListener(this::onDisconnected);
    }

    /** 单测注入时钟，不注册会话监听。 */
    PresenceService(PresenceConnectionStore store,
                    PresenceAudienceService audience,
                    RedisStreamService redisStreamService,
                    ObjectMapper objectMapper,
                    ServerIdentity serverIdentity,
                    Duration connectionTtl,
                    java.time.Clock clock,
                    long sweepLockMs) {
        this.store = store;
        this.audience = audience;
        this.redisStreamService = redisStreamService;
        this.objectMapper = objectMapper;
        this.serverIdentity = serverIdentity;
        this.connectionTtl = connectionTtl == null || connectionTtl.isZero() || connectionTtl.isNegative()
                ? Duration.ofSeconds(90) : connectionTtl;
        this.clock = clock == null ? java.time.Clock.systemUTC() : clock;
        this.sweepLockMs = sweepLockMs;
    }

    public void onConnected(String userId, String connectionId) {
        if (isBlank(userId) || isBlank(connectionId)) {
            return;
        }
        try {
            int before = store.connect(userId, connectionId, now(), connectionTtl.toMillis());
            if (before == 0 && !store.isHidden(userId)) {
                publish(userId, STATUS_ONLINE, null);
            }
        } catch (Exception e) {
            log.warn("登记在线状态失败: userId={}, connectionId={}, error={}", userId, connectionId, e.getMessage());
        }
    }

    public void onDisconnected(String userId, String connectionId) {
        if (isBlank(userId) || isBlank(connectionId)) {
            return;
        }
        try {
            int code = store.disconnect(userId, connectionId, now());
            if (code == PresenceConnectionStore.DISCONNECT_WENT_OFFLINE && !store.isHidden(userId)) {
                publish(userId, STATUS_OFFLINE, store.getLastSeen(userId));
            }
        } catch (Exception e) {
            log.warn("更新离线状态失败: userId={}, connectionId={}, error={}", userId, connectionId, e.getMessage());
        }
    }

    /**
     * 心跳续期。连接键已经过期时按新连接重新登记（可能因此重新上线）。
     */
    public void onHeartbeat(String userId, String connectionId) {
        if (isBlank(userId) || isBlank(connectionId)) {
            return;
        }
        try {
            if (!store.heartbeat(userId, connectionId, now(), connectionTtl.toMillis())) {
                onConnected(userId, connectionId);
            }
        } catch (Exception e) {
            log.warn("续期在线状态失败: userId={}, connectionId={}, error={}", userId, connectionId, e.getMessage());
        }
    }

    /** 实例崩溃或心跳停止后，连接键过期，由持锁的实例补一次离线推送。 */
    public void sweepExpired() {
        try {
            if (!store.trySweepLock(serverIdentity.getServerId(), sweepLockMs)) {
                return;
            }
            long now = now();
            for (String userId : store.onlineUserIds()) {
                try {
                    if (store.sweepOffline(userId, now) && !store.isHidden(userId)) {
                        publish(userId, STATUS_OFFLINE, store.getLastSeen(userId));
                    }
                } catch (Exception e) {
                    log.warn("清理过期在线状态失败: userId={}, error={}", userId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("在线状态清理失败: {}", e.getMessage());
        }
    }

    public PresenceQueryResponse query(String viewerId, String userIds) {
        List<String> ids = parseUserIds(userIds);
        Set<String> visible = audience.visibleTargets(viewerId, ids);
        if (visible == null) {
            visible = Set.of();
        }
        List<PresenceItemResponse> items = new ArrayList<>(ids.size());
        for (String userId : ids) {
            if (userId.equals(viewerId)) {
                items.add(realItem(userId));
            } else if (visible.contains(userId)) {
                items.add(store.isHidden(userId) ? maskedOffline(userId) : realItem(userId));
            } else {
                items.add(PresenceItemResponse.builder().userId(userId).visible(false).build());
            }
        }
        return PresenceQueryResponse.builder().presences(items).build();
    }

    public PresenceSettingsResponse getSettings(String userId) {
        return PresenceSettingsResponse.builder().hideOnlineStatus(store.isHidden(userId)).build();
    }

    public PresenceSettingsResponse updateSettings(String userId, boolean hide) {
        boolean wasHidden = store.isHidden(userId);
        if (wasHidden == hide) {
            return PresenceSettingsResponse.builder().hideOnlineStatus(hide).build();
        }
        store.setHidden(userId, hide);
        boolean online = store.aliveCount(userId, now()) > 0;
        if (hide) {
            publish(userId, STATUS_OFFLINE, null);
        } else if (online) {
            publish(userId, STATUS_ONLINE, null);
        } else {
            publish(userId, STATUS_OFFLINE, store.getLastSeen(userId));
        }
        return PresenceSettingsResponse.builder().hideOnlineStatus(hide).build();
    }

    private PresenceItemResponse realItem(String userId) {
        boolean online = store.aliveCount(userId, now()) > 0;
        return PresenceItemResponse.builder()
                .userId(userId)
                .visible(true)
                .status(online ? STATUS_ONLINE : STATUS_OFFLINE)
                .lastSeenAt(online ? null : store.getLastSeen(userId))
                .build();
    }

    private PresenceItemResponse maskedOffline(String userId) {
        return PresenceItemResponse.builder()
                .userId(userId)
                .visible(true)
                .status(STATUS_OFFLINE)
                .lastSeenAt(null)
                .build();
    }

    private List<String> parseUserIds(String userIds) {
        if (userIds == null || userIds.isBlank()) {
            throw new IllegalArgumentException("请提供要查询的用户");
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String part : userIds.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("请提供要查询的用户");
        }
        if (ids.size() > MAX_QUERY) {
            throw new IllegalArgumentException("一次最多查询 " + MAX_QUERY + " 个用户");
        }
        return List.copyOf(ids);
    }

    private void publish(String userId, String status, Long lastSeenAt) {
        List<String> watchers;
        try {
            watchers = audience.watchers(userId);
        } catch (Exception e) {
            log.warn("查询在线状态接收者失败: userId={}, error={}", userId, e.getMessage());
            return;
        }
        if (watchers.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", userId);
        body.put("status", status);
        body.put("last_seen_at", lastSeenAt);
        body.put("server_time", now());
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.error("序列化在线状态失败: userId={}", userId, e);
            return;
        }
        for (int i = 0; i < watchers.size(); i += PUBLISH_CHUNK) {
            List<String> chunk = watchers.subList(i, Math.min(i + PUBLISH_CHUNK, watchers.size()));
            try {
                redisStreamService.publishGroupMessage(STREAM_TYPE, userId, json, chunk);
            } catch (Exception e) {
                log.warn("推送在线状态失败: userId={}, error={}", userId, e.getMessage());
            }
        }
    }

    private long now() {
        return clock.millis();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
