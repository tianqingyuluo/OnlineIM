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

class ReadReceiptMessageReceiverTest {

    @Test
    void routesReadReceiptToEachLocalTarget() throws Exception {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        WebSocketFrameSender frameSender = mock(WebSocketFrameSender.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(registry.getByUserId("usr_member")).thenReturn(session);
        when(frameSender.send(eq(session), eq("READ_RECEIPT"), any(ReceiptEventPayload.class))).thenReturn(true);

        ReadReceiptMessageReceiver receiver = new ReadReceiptMessageReceiver(
                new ObjectMapper(), registry, frameSender);
        WebSocketMessageEvent event = WebSocketMessageEvent.builder()
                .type("READ_RECEIPT")
                .receiverIDs(List.of("usr_member"))
                .message("{\"conversation_id\":\"grp_1\",\"reader_id\":\"usr_reader\","
                        + "\"read_seq\":\"20\"}")
                .build();

        assertTrue(receiver.handleMessage(event));
        verify(frameSender).send(eq(session), eq("READ_RECEIPT"), any(ReceiptEventPayload.class));
    }
}
