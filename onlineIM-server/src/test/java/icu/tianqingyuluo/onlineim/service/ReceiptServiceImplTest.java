package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.ReadReceiptRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.ReceiptRequest;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.impl.ReceiptServiceImpl;
import icu.tianqingyuluo.onlineim.websocket.event.RedisStreamEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReceiptServiceImplTest {

    private ConversationReadStateService readStateService;
    private PrivateMessageRepository privateMessageRepository;
    private GroupMessageRepository groupMessageRepository;
    private ConversationRepository conversationRepository;
    private GroupMemberService groupMemberService;
    private RedisStreamService redisStreamService;
    private ReceiptServiceImpl service;

    @BeforeEach
    void setUp() {
        readStateService = mock(ConversationReadStateService.class);
        privateMessageRepository = mock(PrivateMessageRepository.class);
        groupMessageRepository = mock(GroupMessageRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        groupMemberService = mock(GroupMemberService.class);
        redisStreamService = mock(RedisStreamService.class);
        service = new ReceiptServiceImpl(
                new ObjectMapper(),
                readStateService,
                privateMessageRepository,
                groupMessageRepository,
                conversationRepository,
                groupMemberService,
                redisStreamService
        );
    }

    @Test
    void deliveredReceiptRequiresReceiverToMatchPersistedMessage() {
        when(privateMessageRepository.findByIdAndConversationId("msg_1", "conv_1"))
                .thenReturn(PrivateMessage.builder()
                        .id("msg_1")
                        .conversationId("conv_1")
                        .senderId("usr_sender")
                        .receiverId("usr_receiver")
                        .seqId("10")
                        .build());
        when(readStateService.advanceDelivered("conv_1", "usr_receiver", "private", "10"))
                .thenReturn(true);

        assertTrue(service.handleDelivered("usr_receiver", ReceiptRequest.builder()
                .receiptType("delivered")
                .messageId("msg_1")
                .conversationId("conv_1")
                .seqId("10")
                .build()));
        assertFalse(service.handleDelivered("usr_attacker", ReceiptRequest.builder()
                .receiptType("delivered")
                .messageId("msg_1")
                .conversationId("conv_1")
                .seqId("10")
                .build()));

        verify(redisStreamService, times(1)).publishMessage(any(RedisStreamEvent.class));
    }

    @Test
    void duplicateReadCursorDoesNotPublishAnotherEvent() {
        when(conversationRepository.findByIdAndUserIDOrTargetId("conv_1", "usr_reader"))
                .thenReturn(mock(icu.tianqingyuluo.onlineim.pojo.document.Conversation.class));
        when(privateMessageRepository.findTopByConversationIdOrderBySeqIdDesc("conv_1"))
                .thenReturn(PrivateMessage.builder()
                        .conversationId("conv_1")
                        .senderId("usr_sender")
                        .receiverId("usr_reader")
                        .seqId("10")
                        .build());
        when(readStateService.advanceRead("conv_1", "usr_reader", "private", "10"))
                .thenReturn(false);

        assertFalse(service.handleRead("usr_reader", ReadReceiptRequest.builder()
                .conversationId("conv_1")
                .readSeq("10")
                .build()));
        verifyNoInteractions(redisStreamService);
    }
}
