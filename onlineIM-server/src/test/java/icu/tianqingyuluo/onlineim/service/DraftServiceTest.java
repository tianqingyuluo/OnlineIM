package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.DraftException;
import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.ConversationDraft;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.dto.request.DraftSaveRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.DraftResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReplyReferenceResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationDraftRepository;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftServiceTest {

    private static final String ME = "usr_me";
    private static final String PEER = "usr_peer";
    private static final String OTHER = "usr_other";
    private static final String CONV = "conv_usr_meusr_peer";
    private static final String GROUP = "grp_1";

    private ConversationDraftRepository drafts;
    private ConversationRepository conversations;
    private FriendService friends;
    private GroupMemberService members;
    private MessageReplyService replies;
    private RedisStreamService redis;
    private DraftService service;

    @BeforeEach
    void setUp() {
        drafts = mock(ConversationDraftRepository.class);
        conversations = mock(ConversationRepository.class);
        friends = mock(FriendService.class);
        members = mock(GroupMemberService.class);
        replies = mock(MessageReplyService.class);
        redis = mock(RedisStreamService.class);
        service = new DraftService(drafts, conversations, friends, members, replies, redis, new ObjectMapper(), 5000);
        when(drafts.findById(anyString())).thenReturn(Optional.empty());
        when(drafts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversations.findByConversationIdAndUserId(CONV, ME))
                .thenReturn(Conversation.builder().conversationId(CONV).userId(ME).build());
        when(members.isGroupMember(GROUP, ME)).thenReturn(true);
        when(members.getGroupMembers(GROUP)).thenReturn(List.of(member(ME), member(PEER), member(OTHER)));
        when(replies.resolveForResponse(anyString(), any())).thenAnswer(invocation -> {
            MessageReplySnapshot snapshot = invocation.getArgument(1);
            return ReplyReferenceResponse.builder()
                    .messageId(snapshot.getMessageId())
                    .previewText(snapshot.getPreviewText())
                    .state("active")
                    .build();
        });
    }

    private static GroupMemberResponse member(String userId) {
        return GroupMemberResponse.builder().userInfo(UserBriefResponse.builder().userId(userId).build()).build();
    }

    private static DraftSaveRequest request(String content, String replyId, DraftSaveRequest.Mention... mentions) {
        return DraftSaveRequest.builder()
                .content(content)
                .replyToMessageId(replyId)
                .atUsers(mentions.length == 0 ? null : List.of(mentions))
                .build();
    }

    @Test
    void savesTextReplyAndNotifiesTheOwner() throws Exception {
        MessageReplySnapshot snapshot = MessageReplySnapshot.builder()
                .messageId("msg_1").previewText("原文").state("active").build();
        when(replies.createSnapshot(CONV, "msg_1", ME)).thenReturn(snapshot);

        DraftResponse response = service.save(CONV, ME, request("  写了一半  ", "msg_1"));

        assertEquals("  写了一半  ", response.getContent());
        assertEquals("写了一半", response.getDraftPreview());
        assertEquals("msg_1", response.getReplyToMessageId());
        assertEquals("active", response.getReplyTo().getState());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).publishPrivateMessage(eq(DraftService.STREAM_TYPE), eq(ME), payload.capture(), eq(ME));
        JsonNode node = new ObjectMapper().readTree(payload.getValue());
        assertEquals(CONV, node.get("conversation_id").asText());
        assertEquals("写了一半", node.get("draft_preview").asText());
        assertFalse(node.get("cleared").asBoolean());
        verify(drafts).save(argThat(draft -> CONV.equals(draft.getConversationId())
                && "msg_1".equals(draft.getReplyTo().getMessageId())));
    }

    @Test
    void dropsUnavailableReplyButKeepsTheText() {
        when(replies.createSnapshot(CONV, "msg_gone", ME)).thenThrow(new ReplyTargetUnavailableException());

        DraftResponse response = service.save(CONV, ME, request("还在", "msg_gone"));

        assertEquals("还在", response.getContent());
        assertNull(response.getReplyTo());
        verify(drafts).save(argThat(draft -> draft.getReplyTo() == null));
    }

    @Test
    void blankContentClearsAndEmptyClearDoesNotPublish() throws Exception {
        when(drafts.findById(ME + ":" + CONV)).thenReturn(Optional.of(ConversationDraft.builder()
                .id(ME + ":" + CONV).userId(ME).conversationId(CONV).content("旧").updatedAt(1L).build()));

        assertNull(service.save(CONV, ME, request("  \n", null)));

        verify(drafts).deleteById(ME + ":" + CONV);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).publishPrivateMessage(eq("DRAFT_UPDATED"), eq(ME), payload.capture(), eq(ME));
        assertTrue(new ObjectMapper().readTree(payload.getValue()).get("cleared").asBoolean());

        when(drafts.findById(ME + ":" + CONV)).thenReturn(Optional.empty());
        service.clear(CONV, ME);
        verify(redis, times(1)).publishPrivateMessage(any(), any(), any(), any());
    }

    @Test
    void identicalDraftDoesNotRewriteOrPublish() {
        when(drafts.findById(ME + ":" + CONV)).thenReturn(Optional.of(ConversationDraft.builder()
                .id(ME + ":" + CONV).content("一样").conversationId(CONV).updatedAt(5L).build()));

        DraftResponse response = service.save(CONV, ME, request("一样", null));

        assertEquals(5L, response.getUpdatedAt());
        verify(drafts, never()).save(any());
        verify(redis, never()).publishPrivateMessage(any(), any(), any(), any());
    }

    @Test
    void laterSaveOverwrites() {
        when(drafts.findById(ME + ":" + CONV)).thenReturn(Optional.of(ConversationDraft.builder()
                .content("旧").conversationId(CONV).build()));

        DraftResponse response = service.save(CONV, ME, request("新", null));

        assertEquals("新", response.getContent());
        verify(drafts).save(argThat(draft -> "新".equals(draft.getContent()) && draft.getUpdatedAt() != null));
    }

    @Test
    void rejectsOutsidersAndOverlongContent() {
        DraftException outsider = assertThrows(DraftException.class,
                () -> service.save(CONV, OTHER, request("偷看", null)));
        assertEquals(DraftException.NOT_CONVERSATION_MEMBER, outsider.getCode());
        verify(drafts, never()).save(any());

        when(friends.areFriends(ME, PEER)).thenReturn(true);
        when(conversations.findByConversationIdAndUserId(CONV, ME)).thenReturn(null);
        assertEquals("好友也能存", service.save(CONV, ME, request("好友也能存", null)).getContent());

        DraftException tooLong = assertThrows(DraftException.class,
                () -> service.save(CONV, ME, request("x".repeat(5001), null)));
        assertEquals(DraftException.DRAFT_CONTENT_INVALID, tooLong.getCode());
    }

    @Test
    void groupMentionsKeepOnlyCurrentMembersWithNames() {
        DraftResponse response = service.save(GROUP, ME, request("@小明 @路人 你好", null,
                DraftSaveRequest.Mention.builder().userId(PEER).name("小明").build(),
                DraftSaveRequest.Mention.builder().userId(ME).name("我").build(),
                DraftSaveRequest.Mention.builder().userId("usr_left").name("路人").build(),
                DraftSaveRequest.Mention.builder().userId(OTHER).name("  ").build()));

        assertEquals(1, response.getAtUsers().size());
        assertEquals(PEER, response.getAtUsers().getFirst().getUserId());
        assertEquals("小明", response.getAtUsers().getFirst().getName());
        verify(drafts).save(argThat(draft -> draft.getAtUsers().size() == 1));
    }

    @Test
    void privateDraftIgnoresMentions() {
        service.save(CONV, ME, request("你好", null,
                DraftSaveRequest.Mention.builder().userId(PEER).name("对方").build()));
        verify(drafts).save(argThat(draft -> draft.getAtUsers() == null));
        verify(members, never()).getGroupMembers(any());
    }

    @Test
    void listPreviewsAndGetResolveStoredDraft() {
        when(drafts.findByUserId(ME)).thenReturn(List.of(
                ConversationDraft.builder().conversationId(CONV).content("  第一行\n第二 ").build(),
                ConversationDraft.builder().conversationId(GROUP).content("   ").build()));
        assertEquals("第一行 第二", service.previewsOf(ME).get(CONV));
        assertFalse(service.previewsOf(ME).containsKey(GROUP));

        when(drafts.findById(ME + ":" + CONV)).thenReturn(Optional.of(ConversationDraft.builder()
                .conversationId(CONV).content("在吗").updatedAt(9L).build()));
        DraftResponse loaded = service.get(CONV, ME);
        assertEquals("在吗", loaded.getContent());
        assertEquals(9L, loaded.getUpdatedAt());
        assertEquals(DraftException.NOT_CONVERSATION_MEMBER,
                assertThrows(DraftException.class, () -> service.get(GROUP, OTHER)).getCode());
    }

    @Test
    void publishFailureDoesNotRollBackTheSave() {
        doThrow(new RuntimeException("redis down")).when(redis)
                .publishPrivateMessage(any(), any(), any(), any());

        DraftResponse response = service.save(CONV, ME, request("还在", null));

        assertEquals("还在", response.getContent());
        verify(drafts).save(any());
    }
}
