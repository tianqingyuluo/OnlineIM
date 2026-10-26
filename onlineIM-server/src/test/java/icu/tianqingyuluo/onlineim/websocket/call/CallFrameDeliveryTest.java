package icu.tianqingyuluo.onlineim.websocket.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.call.CallFrames;
import icu.tianqingyuluo.onlineim.service.call.CallSignalingService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.handler.HeartbeatHandler;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketMessageRouter;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.MessageTypeSenderRegistry;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.sender.MessageSenderHandler;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallFrameDeliveryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private LocalSessionRegistry registry;
    private CallWebSocketHandlers.CallFrameDelivery delivery;
    private ServerWebSocket firstSocket;
    private ServerWebSocket secondSocket;

    @BeforeEach
    void setUp() {
        registry = mock(LocalSessionRegistry.class);
        delivery = new CallWebSocketHandlers.CallFrameDelivery(mapper, registry);
        firstSocket = mock(ServerWebSocket.class);
        secondSocket = mock(ServerWebSocket.class);
        WebSocketSession first = WebSocketSession.builder().userId("usr_b").connectionId("conn_b1").socket(firstSocket).build();
        WebSocketSession second = WebSocketSession.builder().userId("usr_b").connectionId("conn_b2").socket(secondSocket).build();
        when(registry.getAllByUserId("usr_b")).thenReturn(List.of(first, second));
    }

    @Test
    void inviteReachesEveryOnlineDevice() {
        delivery.deliver(event("{\"call_id\":\"call_abcdefgh\",\"sdp\":\"offer\"}"));

        verify(firstSocket).writeTextMessage(contains("CALL_INVITE"));
        verify(secondSocket).writeTextMessage(contains("offer"));
    }

    @Test
    void handledElsewhereSkipsTheDeviceThatAnswered() {
        delivery.deliver(event("{\"call_id\":\"call_abcdefgh\",\"call_status\":\"answered\",\"exclude_connection_id\":\"conn_b1\"}"));

        verify(secondSocket).writeTextMessage(contains("CALL_HANDLED_ELSEWHERE"));
        verify(firstSocket, org.mockito.Mockito.never()).writeTextMessage(anyString());
        verify(secondSocket).writeTextMessage(org.mockito.ArgumentMatchers.argThat(body -> !body.contains("exclude_connection_id")));
    }

    @Test
    void routerRejectsNonFriendWithErrorFrame() throws Exception {
        CallSignalingService signaling = mock(CallSignalingService.class);
        when(signaling.handleInbound(org.mockito.ArgumentMatchers.any(), eq(CallFrames.INVITE), anyString()))
                .thenThrow(new icu.tianqingyuluo.onlineim.exception.ForbiddenException("你们还不是好友，无法发起通话"));
        List<MessageSenderHandler> handlers = new java.util.ArrayList<>();
        for (String type : CallFrames.INBOUND) {
            handlers.add(new CallWebSocketHandlers.CallFrameSender(signaling, type));
        }
        MessageTypeSenderRegistry senders = new MessageTypeSenderRegistry(handlers);
        senders.init();
        assertTrue(senders.supportMessageType(CallFrames.CANDIDATE));
        assertFalse(senders.supportMessageType(CallFrames.BUSY));

        ServerWebSocket socket = mock(ServerWebSocket.class);
        WebSocketSession session = WebSocketSession.builder().userId("usr_a").connectionId("conn_a").socket(socket).build();
        WebSocketMessageRouter router = new WebSocketMessageRouter(mapper, senders, mock(HeartbeatHandler.class));

        router.route(session, "{\"type\":\"CALL_INVITE\",\"message\":{\"target_id\":\"usr_b\"}}");

        verify(socket).writeTextMessage(contains("\"code\":\"403\""));
        verify(socket).writeTextMessage(contains("你们还不是好友，无法发起通话"));
    }

    private static WebSocketMessageEvent event(String message) {
        return WebSocketMessageEvent.builder()
                .type(message.contains("answered") ? CallFrames.HANDLED_ELSEWHERE : CallFrames.INVITE)
                .senderID("usr_a")
                .receiverIDs(List.of("usr_b"))
                .message(message)
                .build();
    }
}
