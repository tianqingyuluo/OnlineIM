package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecallMessageReceiverTest {

    @Test
    void sendsConfirmationToRecallerAndNotificationToOthers() throws Exception {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        WebSocketSession me = mock(WebSocketSession.class);
        WebSocketSession peer = mock(WebSocketSession.class);
        when(me.isActive()).thenReturn(true);
        when(peer.isActive()).thenReturn(true);
        when(registry.getByUserId("usr_me")).thenReturn(me);
        when(registry.getByUserId("usr_peer")).thenReturn(peer);
        ObjectMapper mapper = new ObjectMapper();
        RecallMessageReceiver receiver = new RecallMessageReceiver(mapper, registry);

        boolean handled = receiver.handleMessage(WebSocketMessageEvent.builder()
                .type("RECALL_MESSAGE")
                .senderID("usr_me")
                .message("{\"message_id\":\"msg_1\",\"conversation_id\":\"conv_1\",\"recall_user_id\":\"usr_me\",\"timestamp\":1}")
                .receiverIDs(List.of("usr_me", "usr_peer", "usr_offline"))
                .build());

        assertTrue(handled);
        ArgumentCaptor<String> mine = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> theirs = ArgumentCaptor.forClass(String.class);
        verify(me).sendMessage(mine.capture());
        verify(peer).sendMessage(theirs.capture());
        assertEquals("RECALL_MESSAGE_RESPONSE", mapper.readTree(mine.getValue()).get("type").asText());
        assertEquals("MESSAGE_RECALLED", mapper.readTree(theirs.getValue()).get("type").asText());
        assertEquals("msg_1", mapper.readTree(theirs.getValue()).get("message_id").asText());
        assertEquals("usr_me", mapper.readTree(theirs.getValue()).get("recall_user_id").asText());
    }
}
