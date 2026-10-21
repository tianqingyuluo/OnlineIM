package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecallMessageSenderTest {

    @Test
    void usesAuthenticatedSessionUserInsteadOfClaimedOperator() {
        MessageService messageService = mock(MessageService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUserId()).thenReturn("usr_me");
        when(messageService.recallMessage("msg_1", "usr_me")).thenReturn(true);
        RecallMessageSender sender = new RecallMessageSender(new ObjectMapper(), messageService);

        assertTrue(sender.publishMessage(session,
                "{\"message_id\":\"msg_1\",\"conversation_id\":\"conv_1\",\"operator_id\":\"usr_other\",\"sender_id\":\"usr_other\"}"));

        verify(messageService).recallMessage("msg_1", "usr_me");
    }

    @Test
    void propagatesRecallRuleViolations() {
        MessageService messageService = mock(MessageService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUserId()).thenReturn("usr_peer");
        when(messageService.recallMessage("msg_1", "usr_peer")).thenThrow(new ForbiddenException("只能撤回自己发送的消息"));
        RecallMessageSender sender = new RecallMessageSender(new ObjectMapper(), messageService);

        assertThrows(ForbiddenException.class,
                () -> sender.publishMessage(session, "{\"message_id\":\"msg_1\",\"conversation_id\":\"conv_1\"}"));
    }
}
