package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.pojo.dto.request.ConversationCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import icu.tianqingyuluo.onlineim.service.ConversationReadStateService;
import icu.tianqingyuluo.onlineim.service.ConversationService;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationControllerCreateTest {

    private ConversationService conversationService;
    private FriendService friendService;
    private GroupMemberService groupMemberService;
    private ConversationController controller;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        friendService = mock(FriendService.class);
        groupMemberService = mock(GroupMemberService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.getUserIDFromToken("Bearer token")).thenReturn("usr_a");
        when(conversationService.openConversation(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mock(ConversationResponse.class));
        controller = new ConversationController(
                conversationService,
                jwtUtil,
                groupMemberService,
                mock(ConversationReadStateService.class),
                friendService);
    }

    @Test
    void privateConversationRequiresFriendship() {
        when(friendService.areFriends("usr_a", "usr_b")).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> controller.createConversation(
                "Bearer token",
                ConversationCreateRequest.builder().targetId("usr_b").type("private").build()));

        verify(conversationService, never()).openConversation(any(), any(), any(), any());
    }

    @Test
    void friendsOpenTheirOwnPrivateRecord() {
        when(friendService.areFriends("usr_a", "usr_b")).thenReturn(true);

        controller.createConversation(
                "Bearer token",
                ConversationCreateRequest.builder().targetId("usr_b").type("private").build());

        verify(conversationService).openConversation("conv_usr_ausr_b", "private", "usr_a", "usr_b");
    }

    @Test
    void groupConversationRequiresMembershipNotFriendship() {
        when(groupMemberService.isGroupMember("grp_1", "usr_a")).thenReturn(true);

        controller.createConversation(
                "Bearer token",
                ConversationCreateRequest.builder().targetId("grp_1").type("group").build());

        verify(friendService, never()).areFriends(any(), any());
        verify(conversationService).openConversation("grp_1", "group", "usr_a", "grp_1");
    }

    @Test
    void nonMemberCannotOpenGroupConversation() {
        when(groupMemberService.isGroupMember("grp_1", "usr_a")).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> controller.createConversation(
                "Bearer token",
                ConversationCreateRequest.builder().targetId("grp_1").type("group").build()));

        verify(conversationService, never()).openConversation(any(), any(), any(), any());
    }

    @Test
    void groupTypeWithNonGroupIdIsRejected() {
        when(groupMemberService.isGroupMember(any(), any())).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> controller.createConversation(
                "Bearer token",
                ConversationCreateRequest.builder().targetId("usr_b").type("group").build()));
    }
}
