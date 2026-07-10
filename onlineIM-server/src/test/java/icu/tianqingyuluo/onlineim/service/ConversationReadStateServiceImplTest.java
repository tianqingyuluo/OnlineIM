package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.document.ConversationReadState;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageReadersResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReadStateResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationReadStateRepository;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.impl.ConversationReadStateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationReadStateServiceImplTest {

    private ConversationReadStateRepository readStateRepository;
    private ConversationRepository conversationRepository;
    private PrivateMessageRepository privateMessageRepository;
    private GroupMessageRepository groupMessageRepository;
    private GroupMemberService groupMemberService;
    private UserService userService;
    private ConversationReadStateServiceImpl service;

    @BeforeEach
    void setUp() {
        readStateRepository = mock(ConversationReadStateRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        privateMessageRepository = mock(PrivateMessageRepository.class);
        groupMessageRepository = mock(GroupMessageRepository.class);
        groupMemberService = mock(GroupMemberService.class);
        userService = mock(UserService.class);
        service = new ConversationReadStateServiceImpl(
                readStateRepository,
                conversationRepository,
                privateMessageRepository,
                groupMessageRepository,
                groupMemberService,
                userService
        );
        when(readStateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void advancingReadAlsoAdvancesDeliveredAndNeverRegresses() {
        ConversationReadState state = state("conv_1", "usr_1", "10", "10");
        when(readStateRepository.findByConversationIdAndUserId("conv_1", "usr_1"))
                .thenReturn(Optional.of(state));

        assertTrue(service.advanceRead("conv_1", "usr_1", "private", "20"));
        assertEquals("20", state.getReadSeq());
        assertEquals("20", state.getDeliveredSeq());

        assertFalse(service.advanceRead("conv_1", "usr_1", "private", "19"));
        assertEquals("20", state.getReadSeq());
        assertEquals("20", state.getDeliveredSeq());
    }

    @Test
    void unreadCountUsesCurrentUsersIncomingMessages() {
        ConversationReadState state = state("conv_1", "usr_reader", "8", "8");
        when(readStateRepository.findByConversationIdAndUserId("conv_1", "usr_reader"))
                .thenReturn(Optional.of(state));
        when(conversationRepository.findByIdAndUserIDOrTargetId("conv_1", "usr_reader"))
                .thenReturn(mock(icu.tianqingyuluo.onlineim.pojo.document.Conversation.class));
        when(privateMessageRepository.findTopByConversationIdOrderBySeqIdDesc("conv_1"))
                .thenReturn(PrivateMessage.builder().seqId("12").build());
        when(privateMessageRepository.countUnreadByConversationIdAndReceiverIdAndSeqIdGreaterThan(
                "conv_1", "usr_reader", "8")).thenReturn(2L);

        ReadStateResponse response = service.getReadState("conv_1", "usr_reader", null);

        assertEquals(2L, response.getUnreadCount());
        verify(privateMessageRepository).countUnreadByConversationIdAndReceiverIdAndSeqIdGreaterThan(
                "conv_1", "usr_reader", "8");
    }

    @Test
    void readersAreDerivedFromGroupMemberReadCursors() {
        when(groupMemberService.isGroupMember("grp_1", "usr_viewer")).thenReturn(true);
        when(groupMessageRepository.findByIdAndGroupId("msg_1", "grp_1"))
                .thenReturn(GroupMessage.builder().id("msg_1").groupId("grp_1").seqId("20").build());
        GroupMemberResponse reader = GroupMemberResponse.builder()
                .userInfo(UserBriefResponse.builder().userId("usr_reader").nickname("已读成员").build())
                .build();
        GroupMemberResponse unread = GroupMemberResponse.builder()
                .userInfo(UserBriefResponse.builder().userId("usr_unread").nickname("未读成员").build())
                .build();
        when(groupMemberService.getGroupMembers("grp_1")).thenReturn(List.of(reader, unread));
        when(readStateRepository.findByConversationId("grp_1")).thenReturn(List.of(
                state("grp_1", "usr_reader", "20", "20"),
                state("grp_1", "usr_unread", "20", "19")
        ));

        MessageReadersResponse response = service.getMessageReaders("grp_1", "msg_1", "usr_viewer");

        assertNotNull(response);
        assertEquals(List.of("usr_reader"), response.getReaders().stream()
                .map(UserBriefResponse::getUserId)
                .toList());
        assertEquals(List.of("usr_reader", "usr_unread"), response.getDeliveredReaders().stream()
                .map(UserBriefResponse::getUserId)
                .toList());
    }

    @Test
    void rejectsInvalidSequence() {
        assertThrows(IllegalArgumentException.class,
                () -> service.advanceRead("conv_1", "usr_1", "private", "bad"));
        verifyNoInteractions(readStateRepository);
    }

    private ConversationReadState state(String conversationId, String userId,
                                        String deliveredSeq, String readSeq) {
        return ConversationReadState.builder()
                .id(conversationId + ":" + userId)
                .conversationId(conversationId)
                .userId(userId)
                .conversationType(conversationId.startsWith("grp_") ? "group" : "private")
                .deliveredSeq(deliveredSeq)
                .readSeq(readSeq)
                .build();
    }
}
