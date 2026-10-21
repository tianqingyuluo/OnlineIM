package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.DeviceSessionResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.RedisConnectionMeta;
import icu.tianqingyuluo.onlineim.util.DeviceTypes;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import icu.tianqingyuluo.onlineim.websocket.session.SessionKickBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多端登录策略：每种设备类型同时只保留一个会话。
 * 同类型新登录会作废旧 token，并通过 WebSocket 通知旧端下线。
 * 网页、桌面、手机可以同时在线。
 */
@Slf4j
@Service
public class DeviceSessionService {

    public static final String SESSION_PREFIX = "user:sessions:";
    public static final String TYPE_INDEX_PREFIX = "user:device-type:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtService jwtService;
    private final SessionKickBus kickBus;
    private final LocalSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final String nodeIp;

    public DeviceSessionService(RedisTemplate<String, Object> redisTemplate,
                                JwtService jwtService,
                                SessionKickBus kickBus,
                                LocalSessionRegistry sessionRegistry,
                                ObjectMapper objectMapper,
                                @Value("${node.ip:}") String nodeIp) {
        this.redisTemplate = redisTemplate;
        this.jwtService = jwtService;
        this.kickBus = kickBus;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.nodeIp = nodeIp == null ? "" : nodeIp;
    }

    /**
     * 登录成功后登记这台设备。同类型的另一台设备会被挤下线。
     */
    public void onLogin(String userId, String deviceId, String deviceType, String token) {
        String safeId = DeviceTypes.sanitizeDeviceId(deviceId);
        if (userId == null || userId.isBlank() || safeId == null) {
            return;
        }
        String type = DeviceTypes.normalize(deviceType);
        String previousId = indexGet(userId, type);
        if (previousId != null && !previousId.equals(safeId)) {
            evict(userId, previousId, type, DeviceTypes.REASON_SAME_TYPE);
        } else if (previousId != null) {
            RedisConnectionMeta existing = read(userId, safeId);
            String oldToken = existing == null ? null : bearer(existing.getToken());
            String nextToken = bearer(token);
            if (oldToken != null && nextToken != null && !oldToken.equals(nextToken)) {
                invalidate(oldToken);
                notifyKicked(userId, safeId, type, DeviceTypes.REASON_RELOGIN);
            }
        }

        RedisConnectionMeta meta = read(userId, safeId);
        boolean replacedOtherDevice = previousId != null && !previousId.equals(safeId);
        if (meta == null || replacedOtherDevice) {
            meta = new RedisConnectionMeta();
            meta.setLoginTime(System.currentTimeMillis());
            meta.setConnectionID(null);
        }
        meta.setToken(bearer(token));
        meta.setUserId(userId);
        meta.setDeviceId(safeId);
        meta.setDeviceType(type);
        meta.setCurrentNodeIP(nodeIp);
        meta.setActiveTime(System.currentTimeMillis());
        if (meta.getLoginTime() == null) {
            meta.setLoginTime(meta.getActiveTime());
        }
        if (previousId != null && previousId.equals(safeId)) {
            // 同一设备重新登录，旧连接已经收到下线通知，等新的 WebSocket 再标在线
            meta.setConnectionID(null);
            meta.setLoginTime(System.currentTimeMillis());
        }
        write(userId, safeId, meta);
        indexPut(userId, type, safeId);
    }

    /**
     * WebSocket 连上后记下连接。若同类型索引仍指向别的设备，再补一次挤下线。
     */
    public void onWebSocketConnected(String userId, String deviceId, String deviceType, String token, String connectionId) {
        String safeId = DeviceTypes.sanitizeDeviceId(deviceId);
        if (userId == null || userId.isBlank() || safeId == null) {
            return;
        }
        RedisConnectionMeta meta = read(userId, safeId);
        String type = deviceType == null || deviceType.isBlank()
                ? (meta != null && meta.getDeviceType() != null ? meta.getDeviceType() : DeviceTypes.WEB)
                : DeviceTypes.normalize(deviceType);
        String indexed = indexGet(userId, type);
        if (indexed != null && !indexed.equals(safeId)) {
            evict(userId, indexed, type, DeviceTypes.REASON_SAME_TYPE);
        }
        if (meta == null) {
            meta = new RedisConnectionMeta();
            meta.setLoginTime(System.currentTimeMillis());
        }
        meta.setToken(bearer(token));
        meta.setUserId(userId);
        meta.setDeviceId(safeId);
        meta.setDeviceType(type);
        meta.setConnectionID(connectionId);
        meta.setCurrentNodeIP(nodeIp);
        meta.setActiveTime(System.currentTimeMillis());
        write(userId, safeId, meta);
        indexPut(userId, type, safeId);
    }

    /**
     * 连接断开只清掉在线标记，登录记录保留到主动登出或被挤下线。
     */
    public void onWebSocketClosed(String userId, String deviceId, String connectionId) {
        if (userId == null || deviceId == null) {
            return;
        }
        RedisConnectionMeta meta = read(userId, deviceId);
        if (meta == null) {
            return;
        }
        if (connectionId != null && meta.getConnectionID() != null && !connectionId.equals(meta.getConnectionID())) {
            return;
        }
        // 同一设备的其他标签页还连着时，设备仍算在线
        WebSocketSession remaining = sessionRegistry.findByDevice(userId, deviceId);
        meta.setConnectionID(remaining == null ? null : remaining.getConnectionId());
        meta.setActiveTime(System.currentTimeMillis());
        write(userId, deviceId, meta);
    }

    public void replaceToken(String userId, String deviceId, String token) {
        if (userId == null || deviceId == null) {
            return;
        }
        RedisConnectionMeta meta = read(userId, deviceId);
        if (meta == null) {
            return;
        }
        meta.setToken(bearer(token));
        meta.setActiveTime(System.currentTimeMillis());
        write(userId, deviceId, meta);
    }

    /**
     * 用户主动登出：删掉这台设备的登录记录并关掉连接，不发“被挤下线”文案。
     */
    public void removeLogin(String userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return;
        }
        String type = findType(userId, deviceId);
        redisTemplate.delete(sessionKey(userId, deviceId));
        if (type != null) {
            redisTemplate.opsForHash().delete(indexKey(userId), type);
        }
        sessionRegistry.closeDevice(userId, deviceId);
    }

    public List<DeviceSessionResponse> list(String userId, String currentDeviceId) {
        if (userId == null) {
            return List.of();
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(indexKey(userId));
        List<DeviceSessionResponse> result = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return result;
        }
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String type = String.valueOf(entry.getKey());
            String deviceId = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            if (deviceId == null) {
                continue;
            }
            RedisConnectionMeta meta = read(userId, deviceId);
            boolean online = meta != null && meta.getConnectionID() != null && !meta.getConnectionID().isBlank();
            Long activeAt = meta == null || meta.getActiveTime() <= 0 ? null : meta.getActiveTime();
            result.add(DeviceSessionResponse.builder()
                    .deviceId(deviceId)
                    .deviceType(DeviceTypes.normalize(meta != null && meta.getDeviceType() != null ? meta.getDeviceType() : type))
                    .online(online)
                    .current(deviceId.equals(currentDeviceId))
                    .loginAt(meta == null ? null : meta.getLoginTime())
                    .activeAt(activeAt)
                    .build());
        }
        result.sort(Comparator.comparing(DeviceSessionResponse::isCurrent).reversed()
                .thenComparing(DeviceSessionResponse::getDeviceType, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    /**
     * @return 这台设备不在登录列表里时返回 false
     */
    public boolean remoteLogout(String userId, String deviceId) {
        String safeId = DeviceTypes.sanitizeDeviceId(deviceId);
        if (userId == null || safeId == null) {
            return false;
        }
        String type = findType(userId, safeId);
        if (type == null) {
            return false;
        }
        evict(userId, safeId, type, DeviceTypes.REASON_REMOTE);
        redisTemplate.opsForHash().delete(indexKey(userId), type);
        return true;
    }

    private void evict(String userId, String deviceId, String deviceType, String reason) {
        RedisConnectionMeta old = read(userId, deviceId);
        if (old != null && old.getToken() != null && !old.getToken().isBlank()) {
            invalidate(bearer(old.getToken()));
        }
        redisTemplate.delete(sessionKey(userId, deviceId));
        notifyKicked(userId, deviceId, deviceType, reason);
        log.info("设备会话已结束: userId={}, deviceId={}, type={}, reason={}", userId, deviceId, deviceType, reason);
    }

    private void notifyKicked(String userId, String deviceId, String deviceType, String reason) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reason", reason);
            payload.put("message", DeviceTypes.kickMessage(reason, deviceType));
            payload.put("device_type", DeviceTypes.normalize(deviceType));
            payload.put("device_id", deviceId);
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", "SESSION_KICKED");
            frame.put("message", payload);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("user_id", userId);
            envelope.put("device_id", deviceId);
            envelope.put("frame", frame);
            kickBus.publish(userId, deviceId, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("发送挤下线通知失败: userId={}, deviceId={}, error={}", userId, deviceId, e.getMessage());
        }
    }

    private void invalidate(String bearerToken) {
        try {
            jwtService.invalidateJWT(bearerToken);
        } catch (RuntimeException e) {
            log.warn("作废被挤下线的 token 失败: {}", e.getMessage());
        }
    }

    private String findType(String userId, String deviceId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(indexKey(userId));
        if (entries == null) {
            return null;
        }
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (entry.getValue() != null && deviceId.equals(String.valueOf(entry.getValue()))) {
                return String.valueOf(entry.getKey());
            }
        }
        return null;
    }

    private String indexGet(String userId, String deviceType) {
        Object value = redisTemplate.opsForHash().get(indexKey(userId), deviceType);
        return value == null ? null : String.valueOf(value);
    }

    private void indexPut(String userId, String deviceType, String deviceId) {
        redisTemplate.opsForHash().put(indexKey(userId), deviceType, deviceId);
    }

    private RedisConnectionMeta read(String userId, String deviceId) {
        return coerce(redisTemplate.opsForValue().get(sessionKey(userId, deviceId)));
    }

    private void write(String userId, String deviceId, RedisConnectionMeta meta) {
        redisTemplate.opsForValue().set(sessionKey(userId, deviceId), meta);
    }

    static String sessionKey(String userId, String deviceId) {
        return SESSION_PREFIX + userId + ":" + deviceId;
    }

    static String indexKey(String userId) {
        return TYPE_INDEX_PREFIX + userId;
    }

    static String bearer(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    static RedisConnectionMeta coerce(Object obj) {
        if (obj instanceof RedisConnectionMeta meta) {
            return meta;
        }
        if (obj instanceof Map<?, ?> map) {
            RedisConnectionMeta meta = new RedisConnectionMeta();
            meta.setToken(str(map.get("token")));
            meta.setConnectionID(str(map.get("connectionID")));
            meta.setCurrentNodeIP(str(map.get("currentNodeIP")));
            meta.setActiveTime(longVal(map.get("activeTime")));
            meta.setUserId(str(map.get("userId")));
            meta.setDeviceId(str(map.get("deviceId")));
            meta.setDeviceType(str(map.get("deviceType")));
            Object loginTime = map.get("loginTime");
            meta.setLoginTime(loginTime == null ? null : longVal(loginTime));
            return meta;
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
