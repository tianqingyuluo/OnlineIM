package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageReplyService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketFrameSender;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GroupMessageSenderTest {

    private GroupMessageRepository messageRepository;
    private GroupMemberService groupMemberService;
    private RedisStreamService redisStreamService;
    private WebSocketFrameSender frameSender;
    private MessageReplyService messageReplyService;
    private GroupMessageSender sender;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        messageRepository = mock(GroupMessageRepository.class);
        groupMemberService = mock(GroupMemberService.class);
        redisStreamService = mock(RedisStreamService.class);
        frameSender = mock(WebSocketFrameSender.class);
        messageReplyService = mock(MessageReplyService.class);
        sender = new GroupMessageSender(
                new ObjectMapper(), messageRepository, redisStreamService, groupMemberService, frameSender, messageReplyService);

        ServerWebSocket socket = mock(ServerWebSocket.class);
        when(socket.isClosed()).thenReturn(false);
        session = WebSocketSession.builder()
                .connectionId("conn_1")
                .userId("usr_sender")
                .socket(socket)
                .build();
        when(groupMemberService.isGroupMember("grp_1", "usr_sender")).thenReturn(true);
        when(groupMemberService.getGroupMembers("grp_1")).thenReturn(List.of(
                GroupMemberResponse.builder()
                        .userInfo(UserBriefResponse.builder().userId("usr_sender").build()).build(),
                GroupMemberResponse.builder()
                        .userInfo(UserBriefResponse.builder().userId("usr_receiver").build()).build()));
    }

    @Test
    void persistsWithSessionIdentityAndAcknowledges() {
        assertTrue(sender.publishMessage(session,
                "{\"group_id\":\"grp_1\",\"sender_id\":\"usr_attacker\","
                        + "\"message_type\":\"text\",\"content\":\"hello\",\"client_message_id\":\"client_1\"}"));

        verify(messageRepository).save(argThat(message ->
                "usr_sender".equals(message.getSenderId())
                        && "grp_1".equals(message.getGroupId())
                        && "client_1".equals(message.getClientMessageId())));
        verify(redisStreamService).publishGroupMessage(
                eq("GROUP_MESSAGE"), eq("usr_sender"), any(String.class), any());
        verify(frameSender).send(eq(session), eq("MESSAGE_ACK"), any());
    }

    @Test
    void duplicateClientIdOnlySendsOriginalAck() {
        GroupMessage existing = GroupMessage.builder()
                .id("msg_existing")
                .groupId("grp_1")
                .senderId("usr_sender")
                .clientMessageId("client_1")
                .seqId("10")
                .build();
        when(messageRepository.findBySenderIdAndClientMessageId("usr_sender", "client_1"))
                .thenReturn(existing);

        assertTrue(sender.publishMessage(session,
                "{\"group_id\":\"grp_1\",\"message_type\":\"text\","
                        + "\"content\":\"hello\",\"client_message_id\":\"client_1\"}"));

        verify(messageRepository, never()).save(any());
        verifyNoInteractions(redisStreamService);
        verify(frameSender).send(eq(session), eq("MESSAGE_ACK"), argThat(payload ->
                payload instanceof icu.tianqingyuluo.onlineim.pojo.dto.response.MessageAckPayload ack
                        && existing.getId().equals(ack.getMessageId())
                        && existing.getClientMessageId().equals(ack.getClientMessageId())));
    }

    @Test
    void rejectsNonMember() {
        when(groupMemberService.isGroupMember("grp_1", "usr_sender")).thenReturn(false);

        assertFalse(sender.publishMessage(session,
                "{\"group_id\":\"grp_1\",\"message_type\":\"text\","
                        + "\"content\":\"hello\",\"client_message_id\":\"client_1\"}"));

        verifyNoInteractions(messageRepository, redisStreamService, frameSender);
    }
}
