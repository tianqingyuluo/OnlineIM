package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.DraftService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DraftUpdatedReceiverTest {

    @Test
    void pushesDraftFrameToTheOwnersConnection() throws Exception {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isActive()).thenReturn(true);
        when(registry.getByUserId("usr_me")).thenReturn(session);
        ObjectMapper mapper = new ObjectMapper();
        DraftUpdatedReceiver receiver = new DraftUpdatedReceiver(mapper, registry);

        assertTrue(receiver.handleMessage(WebSocketMessageEvent.builder()
                .type(DraftService.STREAM_TYPE)
                .senderID("usr_me")
                .message("{\"conversation_id\":\"conv_1\",\"content\":\"hi\",\"cleared\":false}")
                .build()));

        ArgumentCaptor<String> frame = ArgumentCaptor.forClass(String.class);
        verify(session).sendMessage(frame.capture());
        JsonNode node = mapper.readTree(frame.getValue());
        assertEquals("DRAFT_UPDATED", node.get("type").asText());
        assertEquals("hi", node.get("message").get("content").asText());
        assertEquals("conv_1", node.get("message").get("conversation_id").asText());
    }

    @Test
    void skipsWhenTheOwnerIsOfflineOnThisNode() {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        DraftUpdatedReceiver receiver = new DraftUpdatedReceiver(new ObjectMapper(), registry);

        assertTrue(receiver.handleMessage(WebSocketMessageEvent.builder()
                .type(DraftService.STREAM_TYPE)
                .senderID("usr_me")
                .message("{\"conversation_id\":\"conv_1\",\"cleared\":true}")
                .build()));
        verify(registry).getByUserId("usr_me");
    }
}
