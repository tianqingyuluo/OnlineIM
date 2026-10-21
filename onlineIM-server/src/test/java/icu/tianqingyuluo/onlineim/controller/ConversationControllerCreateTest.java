package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.pojo.dto.request.ConversationCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.service.ConversationReadStateService;
import icu.tianqingyuluo.onlineim.service.ConversationService;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationControllerCreateTest {

    private ConversationRepository conversationRepository;
    private FriendService friendService;
    private ConversationController controller;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        friendService = mock(FriendService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.getUserIDFromToken("Bearer token")).thenReturn("usr_a");
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.convertToConversationResponse(any(), eq("usr_a")))
                .thenReturn(mock(ConversationResponse.class));
        controller = new ConversationController(
                conversationService,
                jwtUtil,
                conversationRepository,
                mock(ConversationReadStateService.class),
                friendService);
    }

    @Test
    void privateConversationRequiresFriendship() {
        when(friendService.areFriends("usr_a", "usr_b")).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> controller.createConversation(
                "Bearer token",
                ConversationCreateRequest.builder().targetId("usr_b").type("private").build()));

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void friendsCanCreatePrivateConversation() {
        when(friendService.areFriends("usr_a", "usr_b")).thenReturn(true);

        controller.createConversation(
                "Bearer token",
                ConversationCreateRequest.builder().targetId("usr_b").type("private").build());

        verify(conversationRepository).save(any());
    }

    @Test
    void groupConversationDoesNotRequireFriendship() {
        controller.createConversation(
                "Bearer token",
                ConversationCreateRequest.builder().targetId("grp_1").type("group").build());

        verify(friendService, never()).areFriends(any(), any());
        verify(conversationRepository).save(any());
    }
}
