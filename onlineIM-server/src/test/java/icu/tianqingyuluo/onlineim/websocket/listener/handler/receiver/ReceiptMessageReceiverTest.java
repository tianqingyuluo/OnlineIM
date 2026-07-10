package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReceiptEventPayload;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketFrameSender;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReceiptMessageReceiverTest {

    @Test
    void routesReceiptToEachLocalTarget() throws Exception {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        WebSocketFrameSender frameSender = mock(WebSocketFrameSender.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(registry.getByUserId("usr_sender")).thenReturn(session);
        when(frameSender.send(eq(session), eq("RECEIPT"), any(ReceiptEventPayload.class))).thenReturn(true);

        ReceiptMessageReceiver receiver = new ReceiptMessageReceiver(
                new ObjectMapper(), registry, frameSender);
        WebSocketMessageEvent event = WebSocketMessageEvent.builder()
                .type("RECEIPT")
                .receiverIDs(List.of("usr_sender"))
                .message("{\"receipt_type\":\"delivered\",\"message_id\":\"msg_1\","
                        + "\"conversation_id\":\"conv_1\",\"seq_id\":\"10\","
                        + "\"receiver_id\":\"usr_receiver\"}")
                .build();

        assertTrue(receiver.handleMessage(event));
        verify(frameSender).send(eq(session), eq("RECEIPT"), any(ReceiptEventPayload.class));
    }
}
