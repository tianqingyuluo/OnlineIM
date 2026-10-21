package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.UserFriendMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReadStateResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationServiceImplTest {

    private static final String PRIV = "conv_usr_ausr_b";

    private ConversationRepository repository;
    private ConversationRecordService records;
    private GroupMemberService groupMemberService;
    private GroupService groupService;
    private UserService userService;
    private ConversationReadStateService readStateService;
    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ConversationRepository.class);
        records = mock(ConversationRecordService.class);
        groupMemberService = mock(GroupMemberService.class);
        groupService = mock(GroupService.class);
        userService = mock(UserService.class);
        readStateService = mock(ConversationReadStateService.class);
        when(records.updateOwn(any(), any(), any())).thenReturn(true);
        when(readStateService.getReadState(any(), any(), any())).thenReturn(ReadStateResponse.builder()
                .deliveredSeq("0").readSeq("0").latestSeq("0").unreadCount(4L).build());
        when(userService.getUserInfoByUserID(any())).thenReturn(new UserResponse());
        service = new ConversationServiceImpl(repository, groupService, userService, mock(FriendService.class),
                mock(UserFriendMapper.class), readStateService, groupMemberService, records);
    }

    private Conversation own(String conversationId, String owner, String target, String type) {
        return Conversation.builder().id(conversationId + ":" + owner).conversationId(conversationId)
                .userId(owner).targetId(target).conversationType(type).top(false).mute(false).status(1)
                .updatedAt(new Date()).lastMessage(new Conversation.LastMessage()).build();
    }

    @Test
    void pinMuteDeleteOnlyTouchTheCallersRecord() {
        when(repository.findByConversationIdAndUserId(PRIV, "usr_b")).thenReturn(own(PRIV, "usr_b", "usr_a", "private"));

        assertTrue(service.topConversation(PRIV, "usr_b"));
        assertTrue(service.muteConversation(PRIV, "usr_b"));
        assertTrue(service.deleteConversation(PRIV, "usr_b"));

        verify(records, times(3)).updateOwn(eq(PRIV), eq("usr_b"), any(Update.class));
        verify(records, never()).updateOwn(eq(PRIV), eq("usr_a"), any());
    }

    @Test
    void outsiderWithoutRecordGetsNotFound() {
        assertFalse(service.topConversation(PRIV, "usr_x"));
        assertNull(service.getConversation(PRIV, "usr_x"));
        verify(records, never()).updateOwn(any(), any(), any());
    }

    @Test
    void groupOperationsRequireCurrentMembershipEvenIfStaleRecordExists() {
        when(repository.findByConversationIdAndUserId("grp_1", "usr_c")).thenReturn(own("grp_1", "usr_c", "grp_1", "group"));
        when(groupMemberService.isGroupMember("grp_1", "usr_c")).thenReturn(false);

        assertFalse(service.topConversation("grp_1", "usr_c"));
        assertNull(service.getConversation("grp_1", "usr_c"));
        verify(records, never()).updateOwn(any(), any(), any());
        verify(records, never()).ensureRecord(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void memberWithoutRecordGetsOneLazily() {
        when(groupMemberService.isGroupMember("grp_1", "usr_c")).thenReturn(true);
        when(records.ensureRecord("grp_1", "group", "usr_c", "grp_1", true))
                .thenReturn(own("grp_1", "usr_c", "grp_1", "group"));

        assertTrue(service.muteConversation("grp_1", "usr_c"));
        verify(records).updateOwn(eq("grp_1"), eq("usr_c"), any());
    }

    @Test
    void openPrivateConversationEnsuresHiddenPeerRecordWithoutTouchingPeerState() {
        when(records.ensureRecord(PRIV, "private", "usr_a", "usr_b", true)).thenReturn(own(PRIV, "usr_a", "usr_b", "private"));

        ConversationResponse response = service.openConversation(PRIV, "private", "usr_a", "usr_b");

        assertEquals(PRIV, response.getConversationID());
        verify(records).ensureRecord(PRIV, "private", "usr_b", "usr_a", false);
    }

    @Test
    void listUsesOnlyOwnVisibleRecordsAndPublicConversationId() {
        Conversation mine = own(PRIV, "usr_a", "usr_b", "private");
        mine.setTop(true);
        Conversation group = own("grp_1", "usr_a", "grp_1", "group");
        when(repository.findByUserIdAndStatus(eq("usr_a"), eq(1), any(Sort.class))).thenReturn(List.of(mine, group));
        GroupBriefResponse brief = new GroupBriefResponse();
        brief.setName("G");
        when(groupService.getGroupByID("grp_1")).thenReturn(brief);

        List<ConversationResponse> list = service.getUserConversations("usr_a");

        assertEquals(2, list.size());
        assertEquals(PRIV, list.get(0).getConversationID());
        assertTrue(list.get(0).getIsPinned());
        assertEquals("usr_b", list.get(0).getTargetInfo().getId());
        assertEquals(4, list.get(0).getUnreadCount());
        assertEquals("grp_1", list.get(1).getConversationID());
    }

    @Test
    void listSkipsRecordsWhoseGroupNoLongerExists() {
        when(repository.findByUserIdAndStatus(eq("usr_a"), eq(1), any(Sort.class)))
                .thenReturn(List.of(own("grp_gone", "usr_a", "grp_gone", "group")));
        assertTrue(service.getUserConversations("usr_a").isEmpty());
    }

    @Test
    void friendDeletionHidesBothRecords() {
        service.hidePrivateConversation("usr_b", "usr_a");
        verify(records).hideForAll(PRIV);
    }
}
