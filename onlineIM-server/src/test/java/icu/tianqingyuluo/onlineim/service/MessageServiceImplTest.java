package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.exception.MessageContextUnavailableException;
import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageContextResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReplyReferenceResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.repository.RecallLogRepository;
import icu.tianqingyuluo.onlineim.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageServiceImplTest {

    private PrivateMessageRepository privateMessageRepository;
    private GroupMessageRepository groupMessageRepository;
    private RecallLogRepository recallLogRepository;
    private GroupMemberService groupMemberService;
    private UserService userService;
    private MessageReplyService messageReplyService;
    private ConversationRepository conversationRepository;
    private RedisStreamService redisStreamService;
    private MessageService service;

    @BeforeEach
    void setUp() {
        privateMessageRepository = mock(PrivateMessageRepository.class);
        groupMessageRepository = mock(GroupMessageRepository.class);
        recallLogRepository = mock(RecallLogRepository.class);
        groupMemberService = mock(GroupMemberService.class);
        userService = mock(UserService.class);
        messageReplyService = mock(MessageReplyService.class);
        conversationRepository = mock(ConversationRepository.class);
        redisStreamService = mock(RedisStreamService.class);
        service = new MessageServiceImpl(
                privateMessageRepository,
                groupMessageRepository,
                recallLogRepository,
                groupMemberService,
                userService,
                messageReplyService,
                conversationRepository,
                redisStreamService,
                new ObjectMapper());
    }

    @Test
    void responseContainsResolvedReplyReference() {
        MessageReplySnapshot snapshot = MessageReplySnapshot.builder()
                .messageId("msg_target")
                .previewText("原消息")
                .state("active")
                .build();
        ReplyReferenceResponse resolved = ReplyReferenceResponse.builder()
                .messageId("msg_target")
                .previewText("原消息")
                .state("active")
                .build();
        PrivateMessage message = privateMessage("msg_reply", "200");
        message.setReplyTo(snapshot);
        when(messageReplyService.resolveForResponse("conv_1", snapshot)).thenReturn(resolved);

        assertEquals(resolved, service.convertPrivateMessageToResponse(message).getReplyTo());
    }

    @Test
    void historyResolvesReplyTargetsInOneBatch() {
        MessageReplySnapshot firstSnapshot = MessageReplySnapshot.builder()
                .messageId("msg_target_1")
                .state("active")
                .build();
        MessageReplySnapshot secondSnapshot = MessageReplySnapshot.builder()
                .messageId("msg_target_2")
                .state("active")
                .build();
        PrivateMessage first = privateMessage("msg_reply_1", "201");
        first.setReplyTo(firstSnapshot);
        PrivateMessage second = privateMessage("msg_reply_2", "202");
        second.setReplyTo(secondSnapshot);
        ReplyReferenceResponse firstResolved = ReplyReferenceResponse.builder()
                .messageId("msg_target_1")
                .state("active")
                .build();
        ReplyReferenceResponse secondResolved = ReplyReferenceResponse.builder()
                .messageId("msg_target_2")
                .state("active")
                .build();
        when(privateMessageRepository.findByConversationIdOrderByTimestampDesc(eq("conv_1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(messageReplyService.resolveBatchForResponses(
                "conv_1", List.of(firstSnapshot, secondSnapshot)))
                .thenReturn(List.of(firstResolved, secondResolved));

        List<MessageResponse> history = service.getPrivateHistory("conv_1", null, 20, "usr_me");

        assertEquals(List.of(firstResolved, secondResolved), history.stream()
                .map(MessageResponse::getReplyTo)
                .toList());
        verify(messageReplyService).resolveBatchForResponses(
                "conv_1", List.of(firstSnapshot, secondSnapshot));
        verify(messageReplyService, never()).resolveForResponse(eq("conv_1"), any());
    }

    @Test
    void recallRedactsAllDirectReplySnapshots() {
        PrivateMessage message = privateMessage("msg_target", "100");
        message.setSenderId("usr_me");
        when(privateMessageRepository.findById("msg_target")).thenReturn(Optional.of(message));

        service.recallMessage("msg_target", "usr_me");

        verify(messageReplyService).markTargetRecalled("conv_1", "msg_target");
        verify(privateMessageRepository).save(message);
    }

    @Test
    void returnsNumericOrderedContextWindowAndBoundsSizes() {
        when(conversationRepository.findByIdAndUserIDOrTargetId("conv_1", "usr_me"))
                .thenReturn(Conversation.builder().id("conv_1").userId("usr_me").targetId("usr_peer").build());
        PrivateMessage target = privateMessage("msg_100", "100");
        when(privateMessageRepository.findByIdAndConversationId("msg_100", "conv_1")).thenReturn(target);
        when(privateMessageRepository.findMessagesBeforeSeqId(eq("conv_1"), eq("100"), any(Pageable.class)))
                .thenReturn(List.of(privateMessage("msg_99", "99"), privateMessage("msg_9", "9")));
        when(privateMessageRepository.findMessagesAfterSeqId(eq("conv_1"), eq("100"), any(Pageable.class)))
                .thenReturn(List.of(privateMessage("msg_101", "101")));

        MessageContextResponse context = service.getContext("conv_1", "msg_100", 99, 99, "usr_me");

        assertEquals(List.of("9", "99", "100", "101"), context.getMessages().stream()
                .map(message -> message.getSeqId())
                .toList());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(privateMessageRepository).findMessagesBeforeSeqId(eq("conv_1"), eq("100"), pageable.capture());
        assertEquals(51, pageable.getValue().getPageSize());
    }

    @Test
    void contextDoesNotRevealMessagesOutsideAccessibleConversation() {
        assertThrows(MessageContextUnavailableException.class,
                () -> service.getContext("conv_hidden", "msg_hidden", 20, 20, "usr_me"));
    }

    @Test
    void httpPrivateSendUsesCanonicalConversationIdAndPublishesReply() {
        String conversationId = "conv_usr_meusr_peer";
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .userId("usr_me")
                .targetId("usr_peer")
                .conversationType("private")
                .build();
        MessageReplySnapshot snapshot = MessageReplySnapshot.builder()
                .messageId("msg_target")
                .previewText("原消息")
                .state("active")
                .build();
        ReplyReferenceResponse resolved = ReplyReferenceResponse.builder()
                .messageId("msg_target")
                .previewText("原消息")
                .state("active")
                .build();
        when(conversationRepository.findByIdAndUserIDOrTargetId(conversationId, "usr_me"))
                .thenReturn(conversation);
        when(messageReplyService.createSnapshot(conversationId, "msg_target", "usr_me"))
                .thenReturn(snapshot);
        when(messageReplyService.resolveForResponse(conversationId, snapshot)).thenReturn(resolved);

        MessageResponse response = service.sendMessage(MessageSendRequest.builder()
                .targetId("usr_peer")
                .messageType("text")
                .content("引用回复")
                .replyToMessageId("msg_target")
                .clientMsgId("client_http_private")
                .build(), "usr_me");

        ArgumentCaptor<PrivateMessage> messageCaptor = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(privateMessageRepository).save(messageCaptor.capture());
        PrivateMessage saved = messageCaptor.getValue();
        assertEquals(conversationId, saved.getConversationId());
        assertEquals("usr_me", saved.getSenderId());
        assertEquals("usr_peer", saved.getReceiverId());
        assertEquals("client_http_private", saved.getClientMessageId());
        assertEquals(snapshot, saved.getReplyTo());
        assertEquals(resolved, response.getReplyTo());
        verify(redisStreamService).publishPrivateMessage(
                eq("PRIVATE_MESSAGE"), eq("usr_me"), any(String.class), eq("usr_peer"));
    }

    @Test
    void httpPrivateDuplicateReturnsOriginalWithoutReplacingReplyOrPublishing() {
        String conversationId = "conv_usr_meusr_peer";
        when(conversationRepository.findByIdAndUserIDOrTargetId(conversationId, "usr_me"))
                .thenReturn(Conversation.builder()
                        .id(conversationId)
                        .userId("usr_me")
                        .targetId("usr_peer")
                        .conversationType("private")
                        .build());
        PrivateMessage existing = privateMessage("msg_existing", "100");
        existing.setConversationId(conversationId);
        existing.setSenderId("usr_me");
        existing.setReceiverId("usr_peer");
        existing.setClientMessageId("client_http_private");
        when(privateMessageRepository.findBySenderIdAndClientMessageId("usr_me", "client_http_private"))
                .thenReturn(existing);

        MessageResponse response = service.sendMessage(MessageSendRequest.builder()
                .targetId("usr_peer")
                .messageType("text")
                .content("重复发送")
                .replyToMessageId("msg_now_unavailable")
                .clientMsgId("client_http_private")
                .build(), "usr_me");

        assertEquals("msg_existing", response.getMessageId());
        verify(privateMessageRepository, never()).save(any());
        verify(messageReplyService, never()).createSnapshot(any(), any(), any());
        verifyNoInteractions(redisStreamService);
    }

    @Test
    void httpPrivateSendRejectsConversationMismatchBeforePersistence() {
        String conversationId = "conv_usr_meusr_peer";
        when(conversationRepository.findByIdAndUserIDOrTargetId(conversationId, "usr_me"))
                .thenReturn(Conversation.builder()
                        .id(conversationId)
                        .userId("usr_me")
                        .targetId("usr_other")
                        .conversationType("private")
                        .build());

        assertThrows(ForbiddenException.class, () -> service.sendMessage(MessageSendRequest.builder()
                .targetId("usr_peer")
                .messageType("text")
                .content("越权消息")
                .clientMsgId("client_forbidden")
                .build(), "usr_me"));

        verify(privateMessageRepository, never()).save(any());
        verify(messageReplyService, never()).createSnapshot(any(), any(), any());
        verifyNoInteractions(redisStreamService);
    }

    @Test
    void httpReplyRejectionDoesNotPersistOrPublish() {
        String conversationId = "conv_usr_meusr_peer";
        when(conversationRepository.findByIdAndUserIDOrTargetId(conversationId, "usr_me"))
                .thenReturn(Conversation.builder()
                        .id(conversationId)
                        .userId("usr_me")
                        .targetId("usr_peer")
                        .conversationType("private")
                        .build());
        when(messageReplyService.createSnapshot(conversationId, "msg_recalled", "usr_me"))
                .thenThrow(new ReplyTargetUnavailableException());

        assertThrows(ReplyTargetUnavailableException.class, () -> service.sendMessage(MessageSendRequest.builder()
                .targetId("usr_peer")
                .messageType("text")
                .content("回复已撤回目标")
                .replyToMessageId("msg_recalled")
                .clientMsgId("client_rejected")
                .build(), "usr_me"));

        verify(privateMessageRepository, never()).save(any());
        verifyNoInteractions(redisStreamService);
    }

    @Test
    void httpGroupSendChecksMembershipAndPublishesToMembers() {
        when(groupMemberService.isGroupMember("grp_1", "usr_me")).thenReturn(true);
        when(groupMemberService.getGroupMembers("grp_1")).thenReturn(List.of(
                GroupMemberResponse.builder()
                        .userInfo(UserBriefResponse.builder().userId("usr_me").build())
                        .build(),
                GroupMemberResponse.builder()
                        .userInfo(UserBriefResponse.builder().userId("usr_peer").build())
                        .build()));

        service.sendMessage(MessageSendRequest.builder()
                .targetId("grp_1")
                .messageType("text")
                .content("群消息")
                .clientMsgId("client_http_group")
                .atUserIds(List.of("usr_peer"))
                .build(), "usr_me");

        ArgumentCaptor<GroupMessage> messageCaptor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(groupMessageRepository).save(messageCaptor.capture());
        assertEquals(List.of("usr_peer"), messageCaptor.getValue().getAtUsers());
        verify(redisStreamService).publishGroupMessage(
                eq("GROUP_MESSAGE"), eq("usr_me"), any(String.class), eq(List.of("usr_me", "usr_peer")));
    }

    @Test
    void httpGroupDuplicateReturnsOriginalWithoutPublishingAgain() {
        when(groupMemberService.isGroupMember("grp_1", "usr_me")).thenReturn(true);
        GroupMessage existing = GroupMessage.builder()
                .id("msg_existing_group")
                .groupId("grp_1")
                .senderId("usr_me")
                .messageType("text")
                .content("第一次发送")
                .clientMessageId("client_http_group")
                .seqId("100")
                .status(0)
                .timestamp(new Date())
                .build();
        when(groupMessageRepository.findBySenderIdAndClientMessageId("usr_me", "client_http_group"))
                .thenReturn(existing);

        MessageResponse response = service.sendMessage(MessageSendRequest.builder()
                .targetId("grp_1")
                .messageType("text")
                .content("重复发送")
                .replyToMessageId("msg_now_unavailable")
                .clientMsgId("client_http_group")
                .build(), "usr_me");

        assertEquals("msg_existing_group", response.getMessageId());
        verify(groupMessageRepository, never()).save(any());
        verify(messageReplyService, never()).createSnapshot(any(), any(), any());
        verifyNoInteractions(redisStreamService);
    }

    @Test
    void httpGroupSendRejectsNonMemberBeforePersistence() {
        when(groupMemberService.isGroupMember("grp_1", "usr_me")).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.sendMessage(MessageSendRequest.builder()
                .targetId("grp_1")
                .messageType("text")
                .content("越权群消息")
                .clientMsgId("client_forbidden_group")
                .build(), "usr_me"));

        verify(groupMessageRepository, never()).save(any());
        verifyNoInteractions(redisStreamService);
    }

    private PrivateMessage privateMessage(String id, String seqId) {
        return PrivateMessage.builder()
                .id(id)
                .conversationId("conv_1")
                .senderId("usr_peer")
                .receiverId("usr_me")
                .messageType("text")
                .content("内容")
                .status(1)
                .clientMessageId("client_" + id)
                .seqId(seqId)
                .timestamp(new Date())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
    }
}
