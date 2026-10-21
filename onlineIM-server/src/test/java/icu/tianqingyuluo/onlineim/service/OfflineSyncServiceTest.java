package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.SyncRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.OfflineSyncResponse;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfflineSyncServiceTest {

    private ConversationService conversationService;
    private MessageService messageService;
    private PrivateMessageRepository privateMessageRepository;
    private GroupMessageRepository groupMessageRepository;
    private OfflineSyncService service;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        messageService = mock(MessageService.class);
        privateMessageRepository = mock(PrivateMessageRepository.class);
        groupMessageRepository = mock(GroupMessageRepository.class);
        service = new OfflineSyncService(conversationService, messageService,
                privateMessageRepository, groupMessageRepository);
        when(messageService.convertPrivateMessageToResponse(any())).thenAnswer(invocation -> {
            PrivateMessage message = invocation.getArgument(0);
            return MessageResponse.builder()
                    .messageId(message.getId())
                    .conversationId(message.getConversationId())
                    .seqId(message.getSeqId())
                    .isRecalled(message.getStatus() != null && message.getStatus() == 3)
                    .edited(message.getEditedAt() != null)
                    .build();
        });
        when(messageService.convertGroupMessageToResponse(any())).thenAnswer(invocation -> {
            GroupMessage message = invocation.getArgument(0);
            return MessageResponse.builder()
                    .messageId(message.getId())
                    .conversationId(message.getGroupId())
                    .seqId(message.getSeqId())
                    .isRecalled(message.getStatus() != null && message.getStatus() == 3)
                    .edited(message.getEditedAt() != null)
                    .build();
        });
    }

    @Test
    void pushesMessagesAfterCursorAndRevisionsWithinCursor() {
        when(conversationService.getUserConversations("usr_me")).thenReturn(List.of(
                conversation("conv_1", "10"),
                conversation("grp_1", "8")));
        PrivateMessage newer = privateMessage("msg_new", "conv_1", "20", 0, null);
        PrivateMessage newest = privateMessage("msg_newest", "conv_1", "30", 3, null);
        when(privateMessageRepository.findNewestAfterSeqId("conv_1", "10"))
                .thenReturn(List.of(newest, newer));
        PrivateMessage edited = privateMessage("msg_old", "conv_1", "5", 0, new Date());
        when(privateMessageRepository.findRevisionsAtOrBeforeSeq("conv_1", "10")).thenReturn(List.of(edited));
        when(privateMessageRepository.findByConversationIdAndIdIn(eq("conv_1"), any())).thenReturn(List.of());

        GroupMessage groupMissed = groupMessage("msg_g", "grp_1", "9", 0, null);
        when(groupMessageRepository.findNewestAfterSeqId("grp_1", "8")).thenReturn(List.of(groupMissed));
        GroupMessage recalled = groupMessage("msg_recalled", "grp_1", "4", 3, null);
        when(groupMessageRepository.findRevisionsAtOrBeforeSeq("grp_1", "8")).thenReturn(List.of(recalled));
        when(groupMessageRepository.findByGroupIdAndIdIn(eq("grp_1"), any())).thenReturn(List.of(
                groupMessage("msg_watched", "grp_1", "3", 0, new Date())));

        OfflineSyncResponse response = service.sync("usr_me", SyncRequest.builder()
                .conversations(List.of(
                        SyncRequest.ConversationCursor.builder().conversationId("conv_1").lastSeq("10").build(),
                        SyncRequest.ConversationCursor.builder().conversationId("grp_secret").lastSeq("1").build(),
                        SyncRequest.ConversationCursor.builder().conversationId("grp_1").lastSeq("8").build(),
                        SyncRequest.ConversationCursor.builder().conversationId("conv_1").lastSeq("nope").build()))
                .watch(List.of(SyncRequest.WatchCursor.builder()
                        .conversationId("grp_1")
                        .messageIds(List.of("msg_watched", "msg_g"))
                        .build()))
                .build());

        assertEquals(List.of("conv_1", "grp_1"),
                response.getConversations().stream().map(ConversationResponse::getConversationID).toList());
        assertEquals(List.of("msg_new", "msg_newest", "msg_g"),
                response.getMessages().stream().map(MessageResponse::getMessageId).toList());
        assertTrue(response.getMessages().get(1).getIsRecalled());
        assertEquals(List.of("msg_old", "msg_recalled", "msg_watched"),
                response.getRevisions().stream().map(MessageResponse::getMessageId).toList());
        assertFalse(response.isTruncated());
        verify(privateMessageRepository, never()).findNewestAfterSeqId(eq("grp_secret"), any());
        verify(groupMessageRepository, never()).findNewestAfterSeqId(eq("grp_secret"), any());
    }

    @Test
    void emptyCursorStillReturnsConversationSummaries() {
        when(conversationService.getUserConversations("usr_me")).thenReturn(List.of(conversation("conv_1", "3")));

        OfflineSyncResponse response = service.sync("usr_me", new SyncRequest());

        assertEquals(1, response.getConversations().size());
        assertEquals(3, response.getConversations().getFirst().getUnreadCount());
        assertTrue(response.getMessages().isEmpty());
        assertFalse(response.isTruncated());
        verify(privateMessageRepository, never()).findNewestAfterSeqId(any(), any());
    }

    @Test
    void marksTruncatedWhenAConversationExceedsThePerConversationCap() {
        when(conversationService.getUserConversations("usr_me")).thenReturn(List.of(conversation("conv_1", "0")));
        List<PrivateMessage> newestFirst = new ArrayList<>();
        for (int i = OfflineSyncService.MESSAGE_LIMIT_PER_CONVERSATION + 1; i >= 1; i--) {
            newestFirst.add(privateMessage("msg_" + i, "conv_1", String.valueOf(1000 + i), 0, null));
        }
        when(privateMessageRepository.findNewestAfterSeqId("conv_1", "0")).thenReturn(newestFirst);

        OfflineSyncResponse response = service.sync("usr_me", SyncRequest.builder()
                .conversations(List.of(SyncRequest.ConversationCursor.builder()
                        .conversationId("conv_1").lastSeq("0").build()))
                .build());

        assertTrue(response.isTruncated());
        assertEquals(OfflineSyncService.MESSAGE_LIMIT_PER_CONVERSATION, response.getMessages().size());
        // 留下最新的 50 条，并按 seq 升序交给客户端（丢掉最旧的 msg_1）
        assertEquals("msg_2", response.getMessages().getFirst().getMessageId());
        assertEquals("msg_" + (OfflineSyncService.MESSAGE_LIMIT_PER_CONVERSATION + 1),
                response.getMessages().getLast().getMessageId());
    }

    @Test
    void blankUserReturnsNothing() {
        OfflineSyncResponse response = service.sync("  ", SyncRequest.builder().build());
        assertTrue(response.getConversations().isEmpty());
        verify(conversationService, never()).getUserConversations(any());
    }

    private static ConversationResponse conversation(String id, String unread) {
        return ConversationResponse.builder()
                .conversationID(id)
                .type(id.startsWith("grp_") ? "group" : "private")
                .unreadCount(Integer.valueOf(unread))
                .latestSeq(unread)
                .build();
    }

    private static PrivateMessage privateMessage(String id, String conversationId, String seq,
                                                 int status, Date editedAt) {
        PrivateMessage message = new PrivateMessage();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setSeqId(seq);
        message.setStatus(status);
        message.setEditedAt(editedAt);
        return message;
    }

    private static GroupMessage groupMessage(String id, String groupId, String seq, int status, Date editedAt) {
        GroupMessage message = new GroupMessage();
        message.setId(id);
        message.setGroupId(groupId);
        message.setSeqId(seq);
        message.setStatus(status);
        message.setEditedAt(editedAt);
        return message;
    }
}
