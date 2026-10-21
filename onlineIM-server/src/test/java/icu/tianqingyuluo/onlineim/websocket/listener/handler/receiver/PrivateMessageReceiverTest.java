package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivateMessageReceiverTest {

    @Test
    void deliversToEveryReceiverDeviceAndTheSendersOtherDevices() throws Exception {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        WebSocketSession senderWeb = mock(WebSocketSession.class);
        WebSocketSession senderPhone = mock(WebSocketSession.class);
        WebSocketSession receiverDesktop = mock(WebSocketSession.class);
        when(senderWeb.isActive()).thenReturn(true);
        when(senderPhone.isActive()).thenReturn(true);
        when(receiverDesktop.isActive()).thenReturn(true);
        when(registry.getAllByUserId("usr_sender")).thenReturn(List.of(senderWeb, senderPhone));
        when(registry.getAllByUserId("usr_receiver")).thenReturn(List.of(receiverDesktop));

        MessageService messageService = mock(MessageService.class);
        when(messageService.convertPrivateMessageToResponse(any())).thenReturn(MessageResponse.builder()
                .messageId("msg_1")
                .conversationId("conv_1")
                .content("hello")
                .clientMessageId("c1")
                .build());
        PrivateMessageReceiver receiver = new PrivateMessageReceiver(new ObjectMapper(), messageService, registry);

        boolean handled = receiver.handleMessage(WebSocketMessageEvent.builder()
                .type("PRIVATE_MESSAGE")
                .senderID("usr_sender")
                .message("{\"senderId\":\"usr_sender\",\"receiverId\":\"usr_receiver\",\"content\":\"hello\",\"client_message_id\":\"c1\"}")
                .build());

        assertTrue(handled);
        verify(senderWeb).sendMessage(any());
        verify(senderPhone).sendMessage(any());
        verify(receiverDesktop).sendMessage(any());
    }
}
