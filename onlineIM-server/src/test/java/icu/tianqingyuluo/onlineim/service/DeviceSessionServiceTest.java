package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.DeviceSessionResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.RedisConnectionMeta;
import icu.tianqingyuluo.onlineim.util.DeviceTypes;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.SessionKickBus;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceSessionServiceTest {

    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, Map<Object, Object>> hashes = new HashMap<>();
    private JwtService jwtService;
    private SessionKickBus kickBus;
    private LocalSessionRegistry registry;
    private volatile CyclicBarrier readBarrier;
    private DeviceSessionService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(valueOps.get(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), any());
        when(redis.delete(anyString())).thenAnswer(invocation -> values.remove(invocation.getArgument(0)) != null);
        when(hashOps.get(anyString(), any())).thenAnswer(invocation -> {
            if (readBarrier != null) {
                try {
                    readBarrier.await(300, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    // 修复后登录不再单独读索引，等不到另一方就直接往下走
                }
            }
            Map<Object, Object> hash = hashes.get(invocation.getArgument(0));
            return hash == null ? null : hash.get(invocation.getArgument(1));
        });
        doAnswer(invocation -> {
            hashes.computeIfAbsent(invocation.getArgument(0), key -> new HashMap<>())
                    .put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(hashOps).put(anyString(), any(), any());
        when(hashOps.entries(anyString())).thenAnswer(invocation ->
                new HashMap<>(hashes.getOrDefault(invocation.getArgument(0), Map.of())));
        doAnswer(invocation -> {
            Map<Object, Object> hash = hashes.get(invocation.getArgument(0));
            if (hash != null) {
                hash.remove(invocation.getArgument(1));
            }
            return 1L;
        }).when(hashOps).delete(anyString(), any());

        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer();
        when(redis.getHashValueSerializer()).thenReturn((RedisSerializer) json);
        when(redis.execute(any(RedisScript.class), any(RedisSerializer.class), any(RedisSerializer.class), anyList(), any(), any()))
                .thenAnswer(invocation -> {
                    String key = (String) ((List<?>) invocation.getArgument(3)).get(0);
                    String field = new String((byte[]) invocation.getArgument(4), StandardCharsets.UTF_8);
                    Object value = json.deserialize((byte[]) invocation.getArgument(5));
                    synchronized (hashes) {
                        return hashes.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
                    }
                });
        jwtService = mock(JwtService.class);
        kickBus = mock(SessionKickBus.class);
        registry = mock(LocalSessionRegistry.class);
        service = new DeviceSessionService(redis, jwtService, kickBus, registry,
                objectMapper, "127.0.0.1:8081");
    }

    @Test
    void sameDeviceTypeKicksPreviousSessionAndKeepsOtherTypes() throws Exception {
        service.onLogin("usr_1", "web-old", DeviceTypes.WEB, "Bearer old-web");
        service.onWebSocketConnected("usr_1", "web-old", DeviceTypes.WEB, "Bearer old-web", "conn_old");
        service.onLogin("usr_1", "phone-1", DeviceTypes.PHONE, "Bearer phone");

        service.onLogin("usr_1", "web-new", DeviceTypes.WEB, "Bearer new-web");

        verify(jwtService).invalidateJWT("Bearer old-web");
        verify(jwtService, never()).invalidateJWT("Bearer phone");
        ArgumentCaptor<String> envelope = ArgumentCaptor.forClass(String.class);
        verify(kickBus).publish(eq("usr_1"), eq("web-old"), envelope.capture());
        JsonNode frame = objectMapper.readTree(envelope.getValue()).get("frame");
        assertEquals("SESSION_KICKED", frame.get("type").asText());
        assertEquals(DeviceTypes.REASON_SAME_TYPE, frame.get("message").get("reason").asText());
        assertEquals("您的账号已在其他网页端登录，当前设备已下线", frame.get("message").get("message").asText());
        assertNull(values.get(DeviceSessionService.sessionKey("usr_1", "web-old")));
        assertEquals("web-new", hashes.get(DeviceSessionService.indexKey("usr_1")).get(DeviceTypes.WEB));
        assertEquals("phone-1", hashes.get(DeviceSessionService.indexKey("usr_1")).get(DeviceTypes.PHONE));

        RedisConnectionMeta current = (RedisConnectionMeta) values.get(DeviceSessionService.sessionKey("usr_1", "web-new"));
        assertEquals("Bearer new-web", current.getToken());
        assertNull(current.getConnectionID());
    }

    @Test
    void remoteLogoutInvalidatesOnlyThatDevice() throws Exception {
        service.onLogin("usr_1", "web-1", DeviceTypes.WEB, "Bearer web");
        service.onLogin("usr_1", "desktop-1", DeviceTypes.DESKTOP, "Bearer desktop");

        assertFalse(service.remoteLogout("usr_1", "missing"));
        assertTrue(service.remoteLogout("usr_1", "desktop-1"));

        verify(jwtService).invalidateJWT("Bearer desktop");
        verify(kickBus).publish(eq("usr_1"), eq("desktop-1"), anyString());
        assertNull(values.get(DeviceSessionService.sessionKey("usr_1", "desktop-1")));
        assertFalse(hashes.get(DeviceSessionService.indexKey("usr_1")).containsKey(DeviceTypes.DESKTOP));

        List<DeviceSessionResponse> devices = service.list("usr_1", "web-1");
        assertEquals(1, devices.size());
        assertEquals("web-1", devices.get(0).getDeviceId());
        assertTrue(devices.get(0).isCurrent());
        assertFalse(devices.get(0).isOnline());
    }

    @Test
    void closingSocketKeepsTheLoginButClearsOnlineFlag() {
        service.onLogin("usr_1", "web-1", DeviceTypes.WEB, "Bearer web");
        service.onWebSocketConnected("usr_1", "web-1", DeviceTypes.WEB, "Bearer web", "conn_1");
        assertTrue(service.list("usr_1", "web-1").get(0).isOnline());

        service.onWebSocketClosed("usr_1", "web-1", "conn_1");

        DeviceSessionResponse device = service.list("usr_1", "web-1").get(0);
        assertFalse(device.isOnline());
        assertEquals(DeviceTypes.WEB, device.getDeviceType());
        verify(jwtService, never()).invalidateJWT(anyString());
    }

    @Test
    void closingOneTabKeepsTheDeviceOnlineWhileAnotherTabIsConnected() {
        service.onLogin("usr_1", "web-1", DeviceTypes.WEB, "Bearer web");
        service.onWebSocketConnected("usr_1", "web-1", DeviceTypes.WEB, "Bearer web", "conn_tab1");
        service.onWebSocketConnected("usr_1", "web-1", DeviceTypes.WEB, "Bearer web", "conn_tab2");
        WebSocketSession tab1 = WebSocketSession.builder().connectionId("conn_tab1").userId("usr_1").deviceId("web-1").build();
        when(registry.findByDevice("usr_1", "web-1")).thenReturn(tab1);

        service.onWebSocketClosed("usr_1", "web-1", "conn_tab2");

        assertTrue(service.list("usr_1", "web-1").get(0).isOnline());
        when(registry.findByDevice("usr_1", "web-1")).thenReturn(null);
        service.onWebSocketClosed("usr_1", "web-1", "conn_tab1");
        assertFalse(service.list("usr_1", "web-1").get(0).isOnline());
    }

    @Test
    void concurrentSameTypeLoginsLeaveExactlyOneValidSession() throws Exception {
        service.onLogin("usr_1", "web-old", DeviceTypes.WEB, "Bearer old");
        // 两次登录都先读到 web-old，再各自写索引（修复前的交错）
        readBarrier = new CyclicBarrier(2);
        Thread a = new Thread(() -> service.onLogin("usr_1", "web-a", DeviceTypes.WEB, "Bearer a"));
        Thread b = new Thread(() -> service.onLogin("usr_1", "web-b", DeviceTypes.WEB, "Bearer b"));
        a.start();
        b.start();
        a.join(5000);
        b.join(5000);
        readBarrier = null;

        verify(jwtService).invalidateJWT("Bearer old");
        String winner = (String) hashes.get(DeviceSessionService.indexKey("usr_1")).get(DeviceTypes.WEB);
        String loser = "web-a".equals(winner) ? "b" : "a";
        // 没留在索引里的那一方必须被挤下线：token 作废、登录记录删除
        verify(jwtService).invalidateJWT("Bearer " + loser);
        verify(jwtService, never()).invalidateJWT("Bearer " + winner.substring(4));
        assertNull(values.get(DeviceSessionService.sessionKey("usr_1", "web-" + loser)));
        assertEquals(1, service.list("usr_1", winner).size());
    }

    @Test
    void sameDeviceIdLoggingInAsAnotherTypeIsListedOnce() {
        service.onLogin("usr_1", "dev-1", DeviceTypes.WEB, "Bearer as-web");
        service.onLogin("usr_1", "dev-1", DeviceTypes.PHONE, "Bearer as-phone");

        verify(jwtService).invalidateJWT("Bearer as-web");
        List<DeviceSessionResponse> devices = service.list("usr_1", "dev-1");
        assertEquals(1, devices.size());
        assertEquals(DeviceTypes.PHONE, devices.get(0).getDeviceType());
        assertTrue(service.remoteLogout("usr_1", "dev-1"));
        assertTrue(service.list("usr_1", "dev-1").isEmpty());
    }
}
