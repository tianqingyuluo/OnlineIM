package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.OfflineSyncResponse;
import icu.tianqingyuluo.onlineim.service.OfflineSyncService;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketFrameSender;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncMessageSenderTest {

    private OfflineSyncService offlineSyncService;
    private WebSocketFrameSender frameSender;
    private SyncMessageSender sender;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        offlineSyncService = mock(OfflineSyncService.class);
        frameSender = mock(WebSocketFrameSender.class);
        sender = new SyncMessageSender(new ObjectMapper(), offlineSyncService, frameSender);
        session = WebSocketSession.builder().connectionId("conn_1").userId("usr_me").build();
        when(offlineSyncService.sync(eq("usr_me"), any())).thenReturn(new OfflineSyncResponse());
        when(frameSender.send(eq(session), eq(SyncMessageSender.RESPONSE_TYPE), any())).thenReturn(true);
    }

    @Test
    void repliesWithOfflineSyncForTheAuthenticatedUser() {
        assertTrue(sender.publishMessage(session,
                "{\"user_id\":\"usr_attacker\",\"conversations\":[{\"conversation_id\":\"conv_1\",\"last_seq\":\"10\"}]}"));

        verify(offlineSyncService).sync(eq("usr_me"), argThat(request ->
                request.getConversations() != null
                        && request.getConversations().size() == 1
                        && "conv_1".equals(request.getConversations().getFirst().getConversationId())
                        && "10".equals(request.getConversations().getFirst().getLastSeq())));
        verify(frameSender).send(eq(session), eq("OFFLINE_SYNC"), any());
    }

    @Test
    void rejectsMissingSession() {
        assertFalse(sender.publishMessage(null, "{}"));
    }
}
