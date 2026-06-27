package icu.tianqingyuluo.onlineim.websocket.session;

import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketSessionTest {

    private ServerWebSocket mockSocket;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        mockSocket = Mockito.mock(ServerWebSocket.class);
        Mockito.when(mockSocket.isClosed()).thenReturn(false);
        Mockito.when(mockSocket.textHandlerID()).thenReturn("socket-1");

        session = WebSocketSession.builder()
                .connectionId("conn_test")
                .userId("usr_test")
                .deviceId("dev_test")
                .token("tok_test")
                .socket(mockSocket)
                .build();
    }

    @Test
    void lastActiveAtInitializedOnBuild() {
        assertTrue(session.getLastActiveAt() > 0);
        assertTrue(System.currentTimeMillis() - session.getLastActiveAt() < 1000);
    }

    @Test
    void refreshLastActiveAtUpdatesTimestamp() throws InterruptedException {
        long initial = session.getLastActiveAt();
        Thread.sleep(10);
        session.refreshLastActiveAt();
        assertTrue(session.getLastActiveAt() > initial);
    }

    @Test
    void isHeartbeatTimeoutReturnsFalseForFreshSession() {
        assertFalse(session.isHeartbeatTimeout(90_000));
    }

    @Test
    void isHeartbeatTimeoutReturnsTrueForStaleSession() throws InterruptedException {
        // 用 0 作为超时阈值，任何时间戳都立即超时
        Thread.sleep(5);
        assertTrue(session.isHeartbeatTimeout(0));
    }

    @Test
    void isActiveReturnsTrueWhenSocketOpen() {
        assertTrue(session.isActive());
    }

    @Test
    void isActiveReturnsFalseWhenSocketClosed() {
        Mockito.when(mockSocket.isClosed()).thenReturn(true);
        assertFalse(session.isActive());
    }

    @Test
    void sendMessageReturnsTrueWhenSocketOpen() {
        assertTrue(session.sendMessage("test"));
        Mockito.verify(mockSocket).writeTextMessage("test");
    }

    @Test
    void sendMessageReturnsFalseWhenSocketClosed() {
        Mockito.when(mockSocket.isClosed()).thenReturn(true);
        assertFalse(session.sendMessage("test"));
    }

    @Test
    void getSocketIdReturnsTextHandlerID() {
        assertEquals("socket-1", session.getSocketId());
    }
}
