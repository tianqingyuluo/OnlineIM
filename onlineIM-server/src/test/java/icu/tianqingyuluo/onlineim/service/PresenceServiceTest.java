package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ServerIdentity;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceItemResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceQueryResponse;
import icu.tianqingyuluo.onlineim.service.impl.InMemoryPresenceConnectionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在线状态生命周期：多连接、最后一条才离线、心跳过期、隐藏与查询权限。
 */
class PresenceServiceTest {

    private final InMemoryPresenceConnectionStore store = new InMemoryPresenceConnectionStore();
    private final PresenceAudienceService audience = mock(PresenceAudienceService.class);
    private final RedisStreamService redisStreamService = mock(RedisStreamService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private PresenceService service;

    @BeforeEach
    void setUp() {
        ServerIdentity serverIdentity = mock(ServerIdentity.class);
        when(serverIdentity.getServerId()).thenReturn("server-a");
        when(audience.watchers("usr_a")).thenReturn(List.of("usr_b", "usr_c"));
        when(audience.visibleTargets(eq("usr_b"), anyCollection())).thenReturn(Set.of("usr_a"));
        service = new PresenceService(
                store, audience, redisStreamService, objectMapper, serverIdentity,
                Duration.ofMillis(90_000),
                new Clock() {
                    @Override
                    public ZoneId getZone() {
                        return ZoneId.of("UTC");
                    }

                    @Override
                    public Clock withZone(ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return Instant.ofEpochMilli(now.get());
                    }
                },
                0L);
    }

    @Test
    void firstConnectionPublishesOnlineAndSecondDoesNot() throws Exception {
        service.onConnected("usr_a", "conn_1");
        service.onConnected("usr_a", "conn_2");

        verify(redisStreamService, times(1)).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), org.mockito.ArgumentMatchers.contains("\"status\":\"online\""),
                eq(List.of("usr_b", "usr_c")));
        assertEquals(PresenceService.STATUS_ONLINE, item("usr_b", "usr_a,usr_x").getStatus());
    }

    @Test
    void offlineOnlyWhenTheLastConnectionCloses() throws Exception {
        service.onConnected("usr_a", "conn_1");
        service.onConnected("usr_a", "conn_2");

        now.addAndGet(5_000);
        service.onDisconnected("usr_a", "conn_1");
        verify(redisStreamService, never()).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), org.mockito.ArgumentMatchers.contains("\"status\":\"offline\""),
                eq(List.of("usr_b", "usr_c")));
        assertEquals(PresenceService.STATUS_ONLINE, item("usr_b", "usr_a").getStatus());

        now.addAndGet(1_000);
        service.onDisconnected("usr_a", "conn_2");
        verify(redisStreamService).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), org.mockito.ArgumentMatchers.contains("\"last_seen_at\":" + now.get()),
                eq(List.of("usr_b", "usr_c")));

        PresenceItemResponse offline = item("usr_b", "usr_a");
        assertEquals(PresenceService.STATUS_OFFLINE, offline.getStatus());
        assertEquals(now.get(), offline.getLastSeenAt());
    }

    @Test
    void repeatedDisconnectDoesNotPublishTwice() {
        service.onConnected("usr_a", "conn_1");
        service.onDisconnected("usr_a", "conn_1");
        service.onDisconnected("usr_a", "conn_1");

        verify(redisStreamService, times(1)).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), org.mockito.ArgumentMatchers.contains("\"status\":\"offline\""),
                eq(List.of("usr_b", "usr_c")));
    }

    @Test
    void heartbeatKeepsConnectionAliveAndExpirySweepPublishesOfflineOnce() {
        service.onConnected("usr_a", "conn_1");
        now.addAndGet(60_000);
        service.onHeartbeat("usr_a", "conn_1");
        now.addAndGet(60_000);
        service.sweepExpired();
        assertEquals(PresenceService.STATUS_ONLINE, item("usr_b", "usr_a").getStatus());

        now.addAndGet(90_000);
        service.sweepExpired();
        PresenceItemResponse offline = item("usr_b", "usr_a");
        assertEquals(PresenceService.STATUS_OFFLINE, offline.getStatus());
        assertEquals(now.get(), offline.getLastSeenAt());

        service.sweepExpired();
        verify(redisStreamService, times(1)).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), org.mockito.ArgumentMatchers.contains("\"status\":\"offline\""),
                eq(List.of("usr_b", "usr_c")));
    }

    @Test
    void expiredHeartbeatReRegistersAsOnline() {
        service.onConnected("usr_a", "conn_1");
        now.addAndGet(90_000);
        service.sweepExpired();
        service.onHeartbeat("usr_a", "conn_1");

        assertEquals(PresenceService.STATUS_ONLINE, item("usr_b", "usr_a").getStatus());
        verify(redisStreamService, times(2)).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), org.mockito.ArgumentMatchers.contains("\"status\":\"online\""),
                eq(List.of("usr_b", "usr_c")));
    }

    @Test
    void hiddenUserLooksOfflineAndDoesNotPublishOnline() {
        service.updateSettings("usr_a", true);
        service.onConnected("usr_a", "conn_1");

        verify(redisStreamService, times(1)).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), org.mockito.ArgumentMatchers.contains("\"status\":\"offline\""),
                eq(List.of("usr_b", "usr_c")));
        PresenceItemResponse masked = item("usr_b", "usr_a");
        assertEquals(PresenceService.STATUS_OFFLINE, masked.getStatus());
        assertNull(masked.getLastSeenAt());
        assertTrue(masked.isVisible());

        PresenceItemResponse self = item("usr_a", "usr_a");
        assertEquals(PresenceService.STATUS_ONLINE, self.getStatus());

        service.updateSettings("usr_a", false);
        verify(redisStreamService).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), org.mockito.ArgumentMatchers.contains("\"status\":\"online\""),
                eq(List.of("usr_b", "usr_c")));
        assertTrue(service.getSettings("usr_a").isHideOnlineStatus() == false);
    }

    @Test
    void strangerIsNotVisibleAndBatchIsValidated() {
        service.onConnected("usr_a", "conn_1");
        PresenceQueryResponse response = service.query("usr_b", "usr_a, usr_stranger, usr_a");
        assertEquals(2, response.getPresences().size());
        assertEquals("usr_a", response.getPresences().get(0).getUserId());
        assertFalse(response.getPresences().get(1).isVisible());
        assertNull(response.getPresences().get(1).getStatus());

        assertThrows(IllegalArgumentException.class, () -> service.query("usr_b", " , "));
        String tooMany = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> "usr_" + index)
                .collect(java.util.stream.Collectors.joining(","));
        assertThrows(IllegalArgumentException.class, () -> service.query("usr_b", tooMany));
    }

    private PresenceItemResponse item(String viewerId, String userIds) {
        return service.query(viewerId, userIds).getPresences().stream()
                .filter(item -> "usr_a".equals(item.getUserId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void publishedOfflinePayloadCarriesLastSeen() throws Exception {
        service.onConnected("usr_a", "conn_1");
        now.addAndGet(10);
        service.onDisconnected("usr_a", "conn_1");

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redisStreamService, times(2)).publishGroupMessage(
                eq(PresenceService.STREAM_TYPE), eq("usr_a"), captor.capture(), eq(List.of("usr_b", "usr_c")));
        JsonNode offline = objectMapper.readTree(captor.getAllValues().get(1));
        assertEquals("offline", offline.get("status").asText());
        assertEquals(now.get(), offline.get("last_seen_at").asLong());
        assertTrue(offline.has("server_time"));
    }
}
