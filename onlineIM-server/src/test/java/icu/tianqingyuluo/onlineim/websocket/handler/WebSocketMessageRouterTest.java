package icu.tianqingyuluo.onlineim.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.MessageTypeSenderRegistry;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.sender.MessageSenderHandler;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.vertx.core.http.ServerWebSocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketMessageRouterTest {

    private ObjectMapper objectMapper;
    private MessageTypeSenderRegistry senderRegistry;
    private HeartbeatHandler heartbeatHandler;
    private WebSocketMessageRouter router;
    private ServerWebSocket mockSocket;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        senderRegistry = Mockito.mock(MessageTypeSenderRegistry.class);
        userSessionService = Mockito.mock(UserSessionService.class);
        heartbeatHandler = new HeartbeatHandler(objectMapper, userSessionService);
        router = new WebSocketMessageRouter(objectMapper, senderRegistry, heartbeatHandler);

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

    private UserSessionService userSessionService;

    @Test
    void heartbeatShortCircuitsBeforeRegistry() {
        when(senderRegistry.supportMessageType("HEARTBEAT")).thenReturn(false);

        router.route(session, "{\"type\":\"HEARTBEAT\",\"message\":{}}");

        // HEARTBEAT 不应走 registry
        verify(senderRegistry, never()).getHandler(any());
        verify(senderRegistry, never()).supportMessageType("HEARTBEAT");
        // 应发送 HEARTBEAT_ACK
        verify(mockSocket).writeTextMessage(contains("HEARTBEAT_ACK"));
    }

    @Test
    void routeRefreshesLastActiveAt() throws InterruptedException {
        long initial = session.getLastActiveAt();
        Thread.sleep(10);

        router.route(session, "{\"type\":\"HEARTBEAT\",\"message\":{}}");

        assertTrue(session.getLastActiveAt() > initial);
    }

    @Test
    void businessMessageGoesThroughRegistry() {
        when(senderRegistry.supportMessageType("PRIVATE_MESSAGE_REQUEST")).thenReturn(true);
        MessageSenderHandler handler = mock(MessageSenderHandler.class);
        when(handler.publishMessage(eq(session), any())).thenReturn(true);
        when(senderRegistry.getHandler("PRIVATE_MESSAGE_REQUEST")).thenReturn(handler);

        router.route(session, "{\"type\":\"PRIVATE_MESSAGE_REQUEST\",\"message\":{\"content\":\"hi\"}}");

        verify(senderRegistry).supportMessageType("PRIVATE_MESSAGE_REQUEST");
        verify(handler).publishMessage(eq(session), any());
    }

    @Test
    void rejectedBusinessMessageErrorContainsClientMessageId() {
        when(senderRegistry.supportMessageType("PRIVATE_MESSAGE_REQUEST")).thenReturn(true);
        MessageSenderHandler handler = mock(MessageSenderHandler.class);
        when(handler.publishMessage(eq(session), any())).thenReturn(false);
        when(senderRegistry.getHandler("PRIVATE_MESSAGE_REQUEST")).thenReturn(handler);

        router.route(session, "{\"type\":\"PRIVATE_MESSAGE_REQUEST\",\"message\":{"
                + "\"client_message_id\":\"client_1\"}}");

        verify(mockSocket).writeTextMessage(contains("client_message_id"));
        verify(mockSocket).writeTextMessage(contains("client_1"));
    }

    @Test
    void replyTargetErrorPreservesBusinessCodeAndClientMessageId() {
        when(senderRegistry.supportMessageType("PRIVATE_MESSAGE_REQUEST")).thenReturn(true);
        MessageSenderHandler handler = mock(MessageSenderHandler.class);
        when(handler.publishMessage(eq(session), any())).thenThrow(new ReplyTargetUnavailableException());
        when(senderRegistry.getHandler("PRIVATE_MESSAGE_REQUEST")).thenReturn(handler);

        router.route(session, "{\"type\":\"PRIVATE_MESSAGE_REQUEST\",\"message\":{"
                + "\"client_message_id\":\"client_reply\"}}");

        verify(mockSocket).writeTextMessage(contains("REPLY_TARGET_UNAVAILABLE"));
        verify(mockSocket).writeTextMessage(contains("client_reply"));
    }

    @Test
    void unknownTypeSendsError() {
        when(senderRegistry.supportMessageType("UNKNOWN_TYPE")).thenReturn(false);

        router.route(session, "{\"type\":\"UNKNOWN_TYPE\",\"message\":{}}");

        verify(mockSocket).writeTextMessage(contains("ERROR"));
    }

    @Test
    void nullSessionDoesNothing() {
        router.route(null, "{}");
        verifyNoInteractions(senderRegistry);
    }

    @Test
    void invalidJsonSendsError() {
        router.route(session, "not json");

        verify(mockSocket).writeTextMessage(contains("ERROR"));
    }
}
