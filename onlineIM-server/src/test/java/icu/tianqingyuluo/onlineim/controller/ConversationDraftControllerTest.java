package icu.tianqingyuluo.onlineim.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.DraftSaveRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.DraftResponse;
import icu.tianqingyuluo.onlineim.service.ConversationReadStateService;
import icu.tianqingyuluo.onlineim.service.ConversationService;
import icu.tianqingyuluo.onlineim.service.DraftService;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationDraftControllerTest {

    private DraftService draftService;
    private ConversationController controller;

    @BeforeEach
    void setUp() {
        draftService = mock(DraftService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.getUserIDFromToken("Bearer token")).thenReturn("usr_me");
        when(jwtUtil.getUserIDFromToken("Bearer bad")).thenReturn(null);
        controller = new ConversationController(
                mock(ConversationService.class),
                jwtUtil,
                mock(GroupMemberService.class),
                mock(ConversationReadStateService.class),
                mock(FriendService.class),
                draftService);
    }

    @Test
    void saveRequestAcceptsSnakeCase() throws Exception {
        DraftSaveRequest request = new ObjectMapper().readValue("""
                {
                  "content": "你好 @小明",
                  "reply_to_message_id": "msg_1",
                  "at_users": [{ "user_id": "usr_peer", "name": "小明" }]
                }
                """, DraftSaveRequest.class);
        assertEquals("你好 @小明", request.getContent());
        assertEquals("msg_1", request.getReplyToMessageId());
        assertEquals("usr_peer", request.getAtUsers().getFirst().getUserId());
        assertEquals("小明", request.getAtUsers().getFirst().getName());
        assertTrue(new ObjectMapper().writeValueAsString(DraftResponse.builder()
                .conversationId("conv_1").content("你好").draftPreview("你好").updatedAt(1L).build())
                .contains("\"draft_preview\""));
    }

    @Test
    void endpointsUseTheAuthenticatedUser() {
        DraftSaveRequest request = DraftSaveRequest.builder().content("hi").build();
        when(draftService.save("conv_1", "usr_me", request))
                .thenReturn(DraftResponse.builder().conversationId("conv_1").content("hi").build());
        when(draftService.get("conv_1", "usr_me"))
                .thenReturn(DraftResponse.builder().conversationId("conv_1").content("hi").build());

        ResponseEntity<DraftResponse> saved = controller.saveDraft("conv_1", "Bearer token", request);
        ResponseEntity<DraftResponse> loaded = controller.getDraft("conv_1", "Bearer token");
        ResponseEntity<Void> cleared = controller.clearDraft("conv_1", "Bearer token");

        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertEquals("hi", saved.getBody().getContent());
        assertEquals(HttpStatus.OK, loaded.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, cleared.getStatusCode());
        verify(draftService).clear("conv_1", "usr_me");

        when(draftService.save("conv_1", "usr_me", request)).thenReturn(null);
        assertEquals(HttpStatus.NO_CONTENT, controller.saveDraft("conv_1", "Bearer token", request).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, controller.getDraft("conv_1", "Bearer bad").getStatusCode());
    }
}
