package icu.tianqingyuluo.onlineim.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.vertx.core.http.ServerWebSocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeartbeatHandlerTest {

    private ObjectMapper objectMapper;
    private UserSessionService userSessionService;
    private HeartbeatHandler heartbeatHandler;
    private ServerWebSocket mockSocket;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userSessionService = Mockito.mock(UserSessionService.class);
        heartbeatHandler = new HeartbeatHandler(objectMapper, userSessionService);

        mockSocket = mock(ServerWebSocket.class);
        when(mockSocket.isClosed()).thenReturn(false);
        when(mockSocket.textHandlerID()).thenReturn("socket-1");

        session = WebSocketSession.builder()
                .connectionId("conn_test")
                .userId("usr_test")
                .deviceId("dev_test")
                .token("tok_test")
                .socket(mockSocket)
                .build();
    }

    @Test
    void handleRefreshesLastActiveAt() throws InterruptedException {
        long initial = session.getLastActiveAt();
        Thread.sleep(10);
        heartbeatHandler.handle(session);
        assertTrue(session.getLastActiveAt() > initial);
    }

    @Test
    void handleCallsRefreshActiveTime() {
        heartbeatHandler.handle(session);
        verify(userSessionService).refreshActiveTime("usr_test", "dev_test");
    }

    @Test
    void handleSendsHeartbeatAck() {
        heartbeatHandler.handle(session);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockSocket).writeTextMessage(captor.capture());
        String sent = captor.getValue();
        assertTrue(sent.contains("HEARTBEAT_ACK"));
        assertTrue(sent.contains("serverTime"));
    }

    @Test
    void handleDoesNothingForNullSession() {
        heartbeatHandler.handle(null);
        verifyNoInteractions(userSessionService);
    }

    @Test
    void handleDoesNothingForInactiveSession() {
        when(mockSocket.isClosed()).thenReturn(true);
        heartbeatHandler.handle(session);
        verifyNoInteractions(userSessionService);
    }

    @Test
    void handleSurvivesRedisFailure() {
        doThrow(new RuntimeException("Redis down"))
                .when(userSessionService).refreshActiveTime(any(), any());
        // 不应抛异常
        assertDoesNotThrow(() -> heartbeatHandler.handle(session));
        // 仍然发送了 ACK
        verify(mockSocket).writeTextMessage(anyString());
    }

    @Test
    void typeConstantsAreCorrect() {
        assertEquals("HEARTBEAT", HeartbeatHandler.TYPE_HEARTBEAT);
        assertEquals("HEARTBEAT_ACK", HeartbeatHandler.TYPE_HEARTBEAT_ACK);
    }
}
