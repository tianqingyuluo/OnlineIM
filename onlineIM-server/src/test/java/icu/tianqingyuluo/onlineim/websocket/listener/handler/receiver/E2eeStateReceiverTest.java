package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.E2eeService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class E2eeStateReceiverTest {

    @Test
    void deliversStateToEveryOnlineDeviceOfBothParticipants() {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        WebSocketSession mine = mock(WebSocketSession.class);
        WebSocketSession other = mock(WebSocketSession.class);
        when(registry.getSessionsByUserId("usr_me")).thenReturn(List.of(mine));
        when(registry.getSessionsByUserId("usr_peer")).thenReturn(List.of(other));
        E2eeStateReceiver receiver = new E2eeStateReceiver(new ObjectMapper(), registry);

        boolean handled = receiver.handleMessage(WebSocketMessageEvent.builder()
                .type(E2eeService.STREAM_TYPE)
                .senderID("usr_me")
                .message("{\"conversation_id\":\"conv_1\",\"enabled\":true,\"updated_by\":\"usr_me\"}")
                .receiverIDs(List.of("usr_me", "usr_peer"))
                .build());

        assertTrue(handled);
        verify(mine).sendMessage(org.mockito.ArgumentMatchers.contains("E2EE_STATE_CHANGED"));
        verify(other).sendMessage(org.mockito.ArgumentMatchers.contains("\"enabled\":true"));
    }
}
