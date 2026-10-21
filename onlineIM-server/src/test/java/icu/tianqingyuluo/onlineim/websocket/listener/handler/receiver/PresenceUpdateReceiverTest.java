package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceUpdateReceiverTest {

    @Test
    void deliversToEveryLocalSessionOfEachReceiver() {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        WebSocketSession tab1 = mock(WebSocketSession.class);
        WebSocketSession tab2 = mock(WebSocketSession.class);
        when(registry.getSessionsByUserId("usr_b")).thenReturn(List.of(tab1, tab2));
        PresenceUpdateReceiver receiver = new PresenceUpdateReceiver(new ObjectMapper(), registry);

        boolean handled = receiver.handleMessage(WebSocketMessageEvent.builder()
                .type("PRESENCE_UPDATE")
                .senderID("usr_a")
                .message("{\"user_id\":\"usr_a\",\"status\":\"offline\",\"last_seen_at\":1700000000000,\"server_time\":1700000001000}")
                .receiverIDs(List.of("usr_b"))
                .build());

        assertTrue(handled);
        verify(tab1).sendMessage(contains("\"type\":\"PRESENCE_UPDATE\""));
        verify(tab2).sendMessage(contains("\"last_seen_at\":1700000000000"));
    }

    @Test
    void rejectsPayloadWithoutUser() {
        PresenceUpdateReceiver receiver = new PresenceUpdateReceiver(new ObjectMapper(), mock(LocalSessionRegistry.class));
        boolean handled = receiver.handleMessage(WebSocketMessageEvent.builder()
                .type("PRESENCE_UPDATE")
                .message("{\"status\":\"online\"}")
                .receiverIDs(List.of("usr_b"))
                .build());
        assertFalse(handled);
    }
}
