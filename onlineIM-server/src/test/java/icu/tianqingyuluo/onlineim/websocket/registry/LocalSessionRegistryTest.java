package icu.tianqingyuluo.onlineim.websocket.registry;

import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import io.vertx.core.http.ServerWebSocket;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
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
                .deviceId("dev_" + userId)
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
    void hasUserOnlineReturnsFalseForOfflineUser() {
        registry.register(createSession("conn_1", "usr_1"));

        assertFalse(registry.hasUserOnline(java.util.List.of("usr_2")));
    }
}
