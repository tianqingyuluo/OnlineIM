package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.service.MessageEditService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditMessageReceiverTest {

    @Test
    void pushesLatestMessageToEveryOnlineMemberWithAtMe() throws Exception {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        WebSocketSession me = mock(WebSocketSession.class);
        WebSocketSession peer = mock(WebSocketSession.class);
        when(me.isActive()).thenReturn(true);
        when(peer.isActive()).thenReturn(true);
        when(registry.getByUserId("usr_me")).thenReturn(me);
        when(registry.getByUserId("usr_peer")).thenReturn(peer);
        MessageEditService editService = mock(MessageEditService.class);
        when(editService.loadResponse("grp_1", "msg_1")).thenReturn(MessageResponse.builder()
                .messageId("msg_1").conversationId("grp_1").content("新内容").edited(true).editedAt("2026-10-22 01:00:00")
                .contentRevision(2).mentionedUserIds(List.of("usr_peer")).build());
        ObjectMapper mapper = new ObjectMapper();
        EditMessageReceiver receiver = new EditMessageReceiver(mapper, registry, editService);

        assertTrue(receiver.handleMessage(WebSocketMessageEvent.builder()
                .type("EDIT_MESSAGE")
                .senderID("usr_me")
                .message("{\"message_id\":\"msg_1\",\"conversation_id\":\"grp_1\",\"content_revision\":2}")
                .receiverIDs(List.of("usr_me", "usr_peer", "usr_offline"))
                .build()));

        ArgumentCaptor<String> mine = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> theirs = ArgumentCaptor.forClass(String.class);
        verify(me).sendMessage(mine.capture());
        verify(peer).sendMessage(theirs.capture());
        JsonNode myFrame = mapper.readTree(mine.getValue());
        JsonNode peerFrame = mapper.readTree(theirs.getValue());
        assertEquals("MESSAGE_EDITED", myFrame.get("type").asText());
        assertFalse(myFrame.get("at_me").asBoolean());
        assertTrue(peerFrame.get("at_me").asBoolean());
        assertFalse(myFrame.get("at_all").asBoolean());
        assertFalse(peerFrame.get("at_all").asBoolean());
        assertEquals("新内容", peerFrame.get("message").get("content").asText());
        assertTrue(peerFrame.get("message").get("edited").asBoolean());
        assertEquals(2, peerFrame.get("message").get("content_revision").asInt());
        assertEquals("2026-10-22 01:00:00", peerFrame.get("message").get("edited_at").asText());
        // 消息只读一次
        verify(editService, times(1)).loadResponse("grp_1", "msg_1");
    }

    @Test
    void ignoresOtherTypesAndBrokenPayloads() {
        EditMessageReceiver receiver = new EditMessageReceiver(new ObjectMapper(), mock(LocalSessionRegistry.class),
                mock(MessageEditService.class));
        assertFalse(receiver.handleMessage(WebSocketMessageEvent.builder().type("RECALL_MESSAGE").message("{}").build()));
        assertFalse(receiver.handleMessage(WebSocketMessageEvent.builder().type("EDIT_MESSAGE").message("{}").build()));
    }
}
