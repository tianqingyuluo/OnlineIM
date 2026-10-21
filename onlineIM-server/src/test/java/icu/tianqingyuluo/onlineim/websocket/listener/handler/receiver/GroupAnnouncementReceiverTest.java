package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupAnnouncementReceiverTest {

    @Test
    void stripsCountsFromMembersAndKeepsThemForManagers() throws Exception {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        GroupMemberService members = mock(GroupMemberService.class);
        WebSocketSession admin = mock(WebSocketSession.class);
        WebSocketSession member = mock(WebSocketSession.class);
        when(admin.isActive()).thenReturn(true);
        when(member.isActive()).thenReturn(true);
        when(registry.getByUserId("usr_admin")).thenReturn(admin);
        when(registry.getByUserId("usr_member")).thenReturn(member);
        when(members.isAdminOrOwner("grp_1", "usr_admin")).thenReturn(true);
        when(members.isAdminOrOwner("grp_1", "usr_member")).thenReturn(false);
        ObjectMapper mapper = new ObjectMapper();
        GroupAnnouncementReadReceiver receiver = new GroupAnnouncementReadReceiver(mapper, registry, members);

        assertTrue(receiver.handleMessage(WebSocketMessageEvent.builder()
                .type("GROUP_ANNOUNCEMENT_READ")
                .senderID("usr_member")
                .message("{\"group_id\":\"grp_1\",\"announcement_id\":\"ann_1\",\"user_id\":\"usr_member\",\"read_count\":1,\"unread_count\":2}")
                .receiverIDs(List.of("usr_admin", "usr_member"))
                .build()));

        ArgumentCaptor<String> adminFrame = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> memberFrame = ArgumentCaptor.forClass(String.class);
        verify(admin).sendMessage(adminFrame.capture());
        verify(member).sendMessage(memberFrame.capture());
        JsonNode adminJson = mapper.readTree(adminFrame.getValue());
        JsonNode memberJson = mapper.readTree(memberFrame.getValue());
        assertEquals("GROUP_ANNOUNCEMENT_READ", adminJson.get("type").asText());
        assertEquals(1, adminJson.get("message").get("read_count").asInt());
        assertFalse(memberJson.get("message").has("read_count"));
        assertFalse(memberJson.get("message").has("unread_count"));
        assertEquals("usr_member", memberJson.get("message").get("user_id").asText());
    }

    @Test
    void deliversTheSameFrameToEveryConnectionOfTheReader() throws Exception {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        GroupMemberService members = mock(GroupMemberService.class);
        WebSocketSession phone = mock(WebSocketSession.class);
        WebSocketSession desktop = mock(WebSocketSession.class);
        when(phone.isActive()).thenReturn(true);
        when(desktop.isActive()).thenReturn(true);
        when(registry.getAllByUserId("usr_member")).thenReturn(List.of(phone, desktop));
        when(members.isAdminOrOwner("grp_1", "usr_member")).thenReturn(false);
        ObjectMapper mapper = new ObjectMapper();
        GroupAnnouncementReadReceiver receiver = new GroupAnnouncementReadReceiver(mapper, registry, members);

        assertTrue(receiver.handleMessage(WebSocketMessageEvent.builder()
                .type("GROUP_ANNOUNCEMENT_READ")
                .senderID("usr_member")
                .message("{\"group_id\":\"grp_1\",\"announcement_id\":\"ann_1\",\"user_id\":\"usr_member\",\"read_count\":1,\"unread_count\":2}")
                .receiverIDs(List.of("usr_member"))
                .build()));

        verify(phone).sendMessage(argThat(body -> body.contains("\"user_id\":\"usr_member\"")
                && !body.contains("\"read_count\"") && !body.contains("unread_count")));
        verify(desktop).sendMessage(argThat(body -> body.contains("\"user_id\":\"usr_member\"")
                && !body.contains("\"read_count\"") && !body.contains("unread_count")));
    }

    @Test
    void ignoresOtherStreamTypes() {
        GroupAnnouncementReceiver receiver = new GroupAnnouncementReceiver(
                new ObjectMapper(), mock(LocalSessionRegistry.class), mock(GroupMemberService.class));
        assertFalse(receiver.handleMessage(WebSocketMessageEvent.builder().type("GROUP_MESSAGE").message("{}").build()));
    }
}
