package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReplyReferenceResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.impl.MessageReplyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageReplyServiceImplTest {

    private PrivateMessageRepository privateMessageRepository;
    private GroupMessageRepository groupMessageRepository;
    private ConversationRepository conversationRepository;
    private GroupMemberService groupMemberService;
    private UserService userService;
    private MessageReplyService service;

    @BeforeEach
    void setUp() {
        privateMessageRepository = mock(PrivateMessageRepository.class);
        groupMessageRepository = mock(GroupMessageRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        groupMemberService = mock(GroupMemberService.class);
        userService = mock(UserService.class);
        service = new MessageReplyServiceImpl(
                privateMessageRepository,
                groupMessageRepository,
                conversationRepository,
                groupMemberService,
                userService,
                mock(MongoTemplate.class));
    }

    @Test
    void createsAuthoritativePrivateSnapshotAndNormalizesTextPreview() {
        String content = "  第一行\n\n" + "好".repeat(85) + "  ";
        PrivateMessage target = PrivateMessage.builder()
                .id("msg_target")
                .conversationId("conv_1")
                .senderId("usr_peer")
                .receiverId("usr_me")
                .messageType("text")
                .content(content)
                .status(1)
                .seqId("100")
                .contentRevision(2)
                .build();
        when(conversationRepository.findByIdAndUserIDOrTargetId("conv_1", "usr_me"))
                .thenReturn(Conversation.builder().id("conv_1").userId("usr_me").targetId("usr_peer").build());
        when(privateMessageRepository.findByIdAndConversationId("msg_target", "conv_1")).thenReturn(target);
        when(userService.getUserBriefInfoByID("usr_peer")).thenReturn(UserBriefResponse.builder()
                .userId("usr_peer")
                .nickname("小明")
                .username("ming")
                .build());

        MessageReplySnapshot snapshot = service.createSnapshot("conv_1", "msg_target", "usr_me");

        assertEquals("msg_target", snapshot.getMessageId());
        assertEquals("100", snapshot.getSeqId());
        assertEquals("小明", snapshot.getSenderDisplayName());
        assertEquals(2, snapshot.getContentRevision());
        assertEquals("active", snapshot.getState());
        assertEquals(81, snapshot.getPreviewText().codePointCount(0, snapshot.getPreviewText().length()));
        assertEquals("…", snapshot.getPreviewText().substring(snapshot.getPreviewText().length() - 1));
    }

    @Test
    void usesStablePreviewLabelsForNonTextMessages() {
        when(groupMemberService.isGroupMember("grp_1", "usr_me")).thenReturn(true);
        GroupMessage target = GroupMessage.builder()
                .id("msg_file")
                .groupId("grp_1")
                .senderId("usr_peer")
                .messageType("file")
                .content("https://files.invalid/a")
                .ext(Map.of("file_name", "报告.pdf"))
                .status(1)
                .seqId("101")
                .build();
        when(groupMessageRepository.findByIdAndGroupId("msg_file", "grp_1")).thenReturn(target);
        when(userService.getUserBriefInfoByID("usr_peer")).thenReturn(UserBriefResponse.builder()
                .userId("usr_peer")
                .username("peer")
                .build());

        MessageReplySnapshot snapshot = service.createSnapshot("grp_1", "msg_file", "usr_me");

        assertEquals("[文件] 报告.pdf", snapshot.getPreviewText());
    }

    @Test
    void rejectsRecalledOrInaccessibleTargetsWithoutLeakingExistence() {
        when(conversationRepository.findByIdAndUserIDOrTargetId("conv_1", "usr_me"))
                .thenReturn(Conversation.builder().id("conv_1").userId("usr_me").targetId("usr_peer").build());
        when(privateMessageRepository.findByIdAndConversationId("msg_target", "conv_1"))
                .thenReturn(PrivateMessage.builder()
                        .id("msg_target")
                        .conversationId("conv_1")
                        .status(3)
                        .build());

        ReplyTargetUnavailableException recalled = assertThrows(
                ReplyTargetUnavailableException.class,
                () -> service.createSnapshot("conv_1", "msg_target", "usr_me"));
        assertEquals("REPLY_TARGET_UNAVAILABLE", recalled.getCode());

        ReplyTargetUnavailableException forbidden = assertThrows(
                ReplyTargetUnavailableException.class,
                () -> service.createSnapshot("conv_other", "msg_hidden", "usr_me"));
        assertEquals("REPLY_TARGET_UNAVAILABLE", forbidden.getCode());
    }

    @Test
    void resolvesEditedAndRecalledStatesWithoutLeakingRecalledPreview() {
        MessageReplySnapshot snapshot = MessageReplySnapshot.builder()
                .messageId("msg_target")
                .seqId("100")
                .senderId("usr_peer")
                .senderDisplayName("小明")
                .messageType("text")
                .previewText("发送时内容")
                .contentRevision(1)
                .state("active")
                .build();
        when(privateMessageRepository.findByConversationIdAndIdIn(eq("conv_1"), anyCollection()))
                .thenReturn(List.of(PrivateMessage.builder()
                        .id("msg_target")
                        .conversationId("conv_1")
                        .status(1)
                        .contentRevision(2)
                        .build()));

        ReplyReferenceResponse edited = service.resolveForResponse("conv_1", snapshot);
        assertEquals("edited", edited.getState());
        assertEquals("发送时内容", edited.getPreviewText());

        when(privateMessageRepository.findByConversationIdAndIdIn(eq("conv_1"), anyCollection()))
                .thenReturn(List.of(PrivateMessage.builder()
                        .id("msg_target")
                        .conversationId("conv_1")
                        .status(3)
                        .contentRevision(2)
                        .build()));

        ReplyReferenceResponse recalled = service.resolveForResponse("conv_1", snapshot);
        assertEquals("recalled", recalled.getState());
        assertNull(recalled.getPreviewText());
    }

    @Test
    void resolvesHistoryRepliesWithOneBatchLookup() {
        MessageReplySnapshot activeSnapshot = MessageReplySnapshot.builder()
                .messageId("msg_active")
                .seqId("100")
                .senderId("usr_peer")
                .senderDisplayName("小明")
                .messageType("text")
                .previewText("发送时内容")
                .contentRevision(1)
                .state("active")
                .build();
        MessageReplySnapshot recalledSnapshot = MessageReplySnapshot.builder()
                .messageId("msg_recalled")
                .seqId("101")
                .senderId("usr_peer")
                .senderDisplayName("小明")
                .messageType("text")
                .previewText("不能泄露")
                .contentRevision(1)
                .state("active")
                .build();
        when(privateMessageRepository.findByConversationIdAndIdIn(
                eq("conv_1"), anyCollection()))
                .thenReturn(List.of(
                        PrivateMessage.builder()
                                .id("msg_active")
                                .conversationId("conv_1")
                                .status(1)
                                .contentRevision(2)
                                .build(),
                        PrivateMessage.builder()
                                .id("msg_recalled")
                                .conversationId("conv_1")
                                .status(3)
                                .contentRevision(1)
                                .build()));

        List<ReplyReferenceResponse> resolved = service.resolveBatchForResponses(
                "conv_1", List.of(activeSnapshot, recalledSnapshot));

        assertEquals("edited", resolved.get(0).getState());
        assertEquals("发送时内容", resolved.get(0).getPreviewText());
        assertEquals("recalled", resolved.get(1).getState());
        assertNull(resolved.get(1).getPreviewText());
        verify(privateMessageRepository).findByConversationIdAndIdIn(eq("conv_1"), anyCollection());
        verify(privateMessageRepository, never()).findByIdAndConversationId(anyString(), anyString());
    }
}
