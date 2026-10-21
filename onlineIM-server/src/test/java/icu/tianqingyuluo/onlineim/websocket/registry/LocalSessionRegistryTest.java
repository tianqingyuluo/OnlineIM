package icu.tianqingyuluo.onlineim.websocket.registry;

import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import io.vertx.core.http.ServerWebSocket;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocalSessionRegistryTest {

    private LocalSessionRegistry registry;
    private TaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        taskScheduler = mock(TaskScheduler.class);
        registry = new LocalSessionRegistry(taskScheduler);
    }

    private WebSocketSession createSession(String connectionId, String userId) {
        ServerWebSocket mockSocket = mock(ServerWebSocket.class);
        when(mockSocket.isClosed()).thenReturn(false);
        when(mockSocket.textHandlerID()).thenReturn("socket-" + connectionId);

        return WebSocketSession.builder()
                .connectionId(connectionId)
                .userId(userId)
                .deviceId("dev_" + connectionId)
                .token("tok_" + userId)
                .socket(mockSocket)
                .build();
    }

    @Test
    void registerAndRetrieveByConnectionId() {
        WebSocketSession session = createSession("conn_1", "usr_1");
        registry.register(session);

        assertEquals(session, registry.getByConnectionId("conn_1"));
    }

    @Test
    void registerAndRetrieveByUserId() {
        WebSocketSession session = createSession("conn_1", "usr_1");
        registry.register(session);

        assertEquals(session, registry.getByUserId("usr_1"));
    }

    @Test
    void unregisterRemovesSession() {
        WebSocketSession session = createSession("conn_1", "usr_1");
        registry.register(session);
        registry.unregister("conn_1");

        assertNull(registry.getByConnectionId("conn_1"));
        assertNull(registry.getByUserId("usr_1"));
    }

    @Test
    void getOnlineCountReturnsCorrectNumber() {
        registry.register(createSession("conn_1", "usr_1"));
        registry.register(createSession("conn_2", "usr_2"));

        assertEquals(2, registry.getOnlineCount());
    }

    @Test
    void cleanupRemovesInactiveSessions() {
        WebSocketSession activeSession = createSession("conn_1", "usr_1");
        registry.register(activeSession);

        WebSocketSession closedSession = createSession("conn_2", "usr_2");
        // 模拟 socket 已关闭
        ServerWebSocket closedSocket = closedSession.getSocket();
        when(closedSocket.isClosed()).thenReturn(true);

        registry.register(closedSession);
        assertEquals(2, registry.getOnlineCount());

        registry.cleanupInactiveSessions(90_000);

        assertEquals(1, registry.getOnlineCount());
        assertNotNull(registry.getByConnectionId("conn_1"));
        assertNull(registry.getByConnectionId("conn_2"));
    }

    @Test
    void cleanupRemovesHeartbeatTimeoutSessions() throws InterruptedException {
        WebSocketSession freshSession = createSession("conn_1", "usr_1");
        registry.register(freshSession);

        WebSocketSession staleSession = createSession("conn_2", "usr_2");
        registry.register(staleSession);
        // 模拟 lastActiveAt 为很久以前（通过 isHeartbeatTimeout 判定）
        // 直接用极短超时阈值让 staleSession 超时
        Thread.sleep(5);

        registry.cleanupInactiveSessions(1);

        // fresh session 因刚创建不会超时（除非花了 >1ms，但都是新的）
        // stale session 也会超时因为阈值只有 1ms
        // 两个都可能超时，因为阈值 1ms 极短
        // 这个测试验证超时逻辑生效，不关心具体保留哪个
        assertTrue(registry.getOnlineCount() <= 1);
    }

    @Test
    void cleanupClosesTimeoutSessionSocket() throws InterruptedException {
        WebSocketSession session = createSession("conn_1", "usr_1");
        registry.register(session);

        // 等待确保 lastActiveAt 的时间戳确实在过去
        Thread.sleep(10);
        // 用 1ms 超时，让 session 超时
        registry.cleanupInactiveSessions(1);

        // 验证 socket 被 close
        verify(session.getSocket()).close();
        assertEquals(0, registry.getOnlineCount());
    }

    @Test
    void cleanupKeepsActiveAndFreshSessions() {
        WebSocketSession session = createSession("conn_1", "usr_1");
        registry.register(session);

        // 用大超时阈值，不应清理
        registry.cleanupInactiveSessions(999_999_999);

        assertEquals(1, registry.getOnlineCount());
        assertNotNull(registry.getByConnectionId("conn_1"));
    }

    @Test
    void hasUserOnlineReturnsTrueForOnlineUser() {
        registry.register(createSession("conn_1", "usr_1"));

        assertTrue(registry.hasUserOnline(java.util.List.of("usr_1")));
    }

    @Test
    void secondConnectionSurvivesWhenTheFirstCloses() {
        WebSocketSession first = createSession("conn_1", "usr_1");
        WebSocketSession second = createSession("conn_2", "usr_1");
        registry.register(first);
        registry.register(second);

        assertEquals(2, registry.getSessionsByUserId("usr_1").size());
        registry.unregister("conn_1");

        assertEquals(second, registry.getByUserId("usr_1"));
        assertEquals(List.of(second), registry.getSessionsByUserId("usr_1"));
        assertTrue(registry.hasUserOnline(List.of("usr_1")));
        assertEquals(1, registry.getOnlineCount());
    }

    @Test
    void removalListenerFiresOncePerClosedConnection() {
        java.util.List<String> removed = new java.util.ArrayList<>();
        registry.addRemovalListener((userId, connectionId) -> removed.add(connectionId));
        registry.register(createSession("conn_1", "usr_1"));
        registry.register(createSession("conn_2", "usr_1"));

        registry.unregister("conn_1");
        registry.unregister("conn_1");

        assertEquals(List.of("conn_1"), removed);
        assertNotNull(registry.getByUserId("usr_1"));
    }

    @Test
    void hasUserOnlineReturnsFalseForOfflineUser() {
        registry.register(createSession("conn_1", "usr_1"));

        assertFalse(registry.hasUserOnline(java.util.List.of("usr_2")));
    }

    @Test
    void oneUserCanKeepASessionPerDevice() {
        registry.register(session("conn_web", "usr_1", "web-1"));
        registry.register(session("conn_phone", "usr_1", "phone-1"));

        assertEquals(2, registry.getAllByUserId("usr_1").size());
        assertEquals(2, registry.getOnlineCount());

        registry.unregister("conn_web");

        assertEquals(1, registry.getAllByUserId("usr_1").size());
        assertEquals("conn_phone", registry.getByUserId("usr_1").getConnectionId());
        assertNotNull(registry.getByConnectionId("conn_phone"));
    }

    @Test
    void sameDeviceTabsKeepTheirConnections() {
        WebSocketSession web = session("conn_web", "usr_1", "web-1");
        registry.register(web);
        registry.register(session("conn_phone", "usr_1", "phone-1"));
        registry.register(session("conn_web_2", "usr_1", "web-1"));

        // 同一浏览器两个标签页共用 device_id，第二个连上后第一个不能被关掉
        assertEquals(3, registry.getOnlineCount());
        assertNotNull(registry.getByConnectionId("conn_web"));
        assertNotNull(registry.getByConnectionId("conn_web_2"));
        assertNotNull(registry.getByConnectionId("conn_phone"));
        verify(web.getSocket(), never()).close(anyShort(), anyString());

        registry.unregister("conn_web_2");
        assertEquals("conn_web", registry.findByDevice("usr_1", "web-1").getConnectionId());
    }

    @Test
    void kickingOneDeviceClosesOnlyThatDevicesConnections() {
        java.util.List<String> removed = new java.util.ArrayList<>();
        registry.addRemovalListener((userId, connectionId) -> removed.add(userId + ":" + connectionId));
        WebSocketSession web = session("conn_web", "usr_1", "web-1");
        WebSocketSession webAgain = session("conn_web_2", "usr_1", "web-1");
        registry.register(web);
        registry.register(session("conn_phone", "usr_1", "phone-1"));
        // 同一设备的两个标签页
        registry.register(webAgain);

        assertTrue(registry.kickDevice("usr_1", "web-1", "{\"type\":\"SESSION_KICKED\"}"));

        assertNull(registry.getByConnectionId("conn_web"));
        assertNull(registry.getByConnectionId("conn_web_2"));
        assertTrue(removed.contains("usr_1:conn_web"));
        verify(web.getSocket()).writeTextMessage(eq("{\"type\":\"SESSION_KICKED\"}"), any());
        assertNotNull(registry.getByConnectionId("conn_phone"));
        assertTrue(registry.hasUserOnline(List.of("usr_1")));
        assertTrue(removed.contains("usr_1:conn_web_2"));
        assertFalse(removed.contains("usr_1:conn_phone"));
        verify(webAgain.getSocket()).writeTextMessage(eq("{\"type\":\"SESSION_KICKED\"}"), any());
    }

    private WebSocketSession session(String connectionId, String userId, String deviceId) {
        ServerWebSocket mockSocket = mock(ServerWebSocket.class);
        when(mockSocket.isClosed()).thenReturn(false);
        when(mockSocket.textHandlerID()).thenReturn("socket-" + connectionId);
        return WebSocketSession.builder()
                .connectionId(connectionId)
                .userId(userId)
                .deviceId(deviceId)
                .token("tok")
                .socket(mockSocket)
                .build();
    }
}
