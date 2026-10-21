package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import icu.tianqingyuluo.onlineim.service.ChatMediaService;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.service.MessageReplyService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketFrameSender;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PrivateMessageSenderTest {
    private static final String CONV = "conv_usr_receiverusr_sender";
    private ConversationRecordService conversationRecords;
    private ChatMediaService chatMediaService;

    private PrivateMessageRepository messageRepository;
    private ConversationRepository conversationRepository;
    private RedisStreamService redisStreamService;
    private MessageReplyService messageReplyService;
    private FriendService friendService;
    private WebSocketFrameSender frameSender;
    private PrivateMessageSender sender;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        messageRepository = mock(PrivateMessageRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        redisStreamService = mock(RedisStreamService.class);
        frameSender = mock(WebSocketFrameSender.class);
        messageReplyService = mock(MessageReplyService.class);
        friendService = mock(FriendService.class);
        conversationRecords = mock(ConversationRecordService.class);
        when(friendService.areFriends(any(), any())).thenReturn(true);
        sender = new PrivateMessageSender(
                new ObjectMapper(), messageRepository, conversationRepository, redisStreamService,
                frameSender, messageReplyService, friendService, conversationRecords,
                chatMediaService = mock(ChatMediaService.class));

        ServerWebSocket socket = mock(ServerWebSocket.class);
        when(socket.isClosed()).thenReturn(false);
        session = WebSocketSession.builder()
                .connectionId("conn_1")
                .userId("usr_sender")
                .socket(socket)
                .build();
        when(conversationRepository.findByConversationIdAndUserId(CONV, "usr_sender"))
                .thenReturn(Conversation.builder()
                        .id(CONV)
                        .userId("usr_sender")
                        .targetId("usr_receiver")
                        .conversationType("private")
                        .build());
    }

    @Test
    void usesSessionIdentityAndReturnsAckAfterPersistence() {
        String payload = """
                {"conversation_id":"conv_usr_receiverusr_sender","sender_id":"usr_attacker","receiver_id":"usr_receiver",
                 "message_type":"text","content":"hello","client_message_id":"client_1"}
                """;

        assertTrue(sender.publishMessage(session, payload));

        verify(conversationRecords).onPrivateMessage(argThat(message -> "usr_receiver".equals(message.getReceiverId())));
        verify(messageRepository).save(argThat(message ->
                "usr_sender".equals(message.getSenderId())
                        && "usr_receiver".equals(message.getReceiverId())
                        && "client_1".equals(message.getClientMessageId())));
        verify(redisStreamService).publishPrivateMessage(
                eq("PRIVATE_MESSAGE"), eq("usr_sender"), any(String.class), eq("usr_receiver"));
        verify(frameSender).send(eq(session), eq("MESSAGE_ACK"), any());
    }

    @Test
    void persistsAuthoritativeReplySnapshotBeforePublishing() {
        MessageReplySnapshot snapshot = MessageReplySnapshot.builder()
                .messageId("msg_target")
                .seqId("9")
                .senderId("usr_receiver")
                .senderDisplayName("对方")
                .messageType("text")
                .previewText("原消息")
                .contentRevision(1)
                .state("active")
                .build();
        when(messageReplyService.createSnapshot(CONV, "msg_target", "usr_sender"))
                .thenReturn(snapshot);

        assertTrue(sender.publishMessage(session,
                "{\"conversation_id\":\"conv_usr_receiverusr_sender\",\"receiver_id\":\"usr_receiver\","
                        + "\"message_type\":\"text\",\"content\":\"reply\","
                        + "\"client_message_id\":\"client_reply\",\"reply_to_message_id\":\"msg_target\"}"));

        verify(messageRepository).save(argThat(message -> snapshot.equals(message.getReplyTo())
                && Integer.valueOf(1).equals(message.getContentRevision())));
        verify(redisStreamService).publishPrivateMessage(
                eq("PRIVATE_MESSAGE"), eq("usr_sender"), any(String.class), eq("usr_receiver"));
        verify(frameSender).send(eq(session), eq("MESSAGE_ACK"), any());
    }

    @Test
    void unavailableReplyTargetDoesNotPersistPublishOrAck() {
        when(messageReplyService.createSnapshot(CONV, "msg_target", "usr_sender"))
                .thenThrow(new ReplyTargetUnavailableException());

        assertThrows(ReplyTargetUnavailableException.class, () -> sender.publishMessage(session,
                "{\"conversation_id\":\"conv_usr_receiverusr_sender\",\"receiver_id\":\"usr_receiver\","
                        + "\"message_type\":\"text\",\"content\":\"reply\","
                        + "\"client_message_id\":\"client_reply\",\"reply_to_message_id\":\"msg_target\"}"));

        verify(messageRepository, never()).save(any());
        verifyNoInteractions(redisStreamService, frameSender);
    }

    @Test
    void duplicateClientIdDoesNotPersistOrPublishAgain() {
        PrivateMessage existing = PrivateMessage.builder()
                .id("msg_existing")
                .conversationId(CONV)
                .senderId("usr_sender")
                .receiverId("usr_receiver")
                .messageType("text")
                .content("hello")
                .clientMessageId("client_1")
                .seqId("10")
                .build();
        when(messageRepository.findBySenderIdAndClientMessageId("usr_sender", "client_1"))
                .thenReturn(existing);

        assertTrue(sender.publishMessage(session,
                "{\"conversation_id\":\"conv_usr_receiverusr_sender\",\"receiver_id\":\"usr_receiver\","
                        + "\"message_type\":\"text\",\"content\":\"hello\",\"client_message_id\":\"client_1\"}"));

        verify(messageRepository, never()).save(any());
        verifyNoInteractions(redisStreamService);
        verify(frameSender).send(eq(session), eq("MESSAGE_ACK"), argThat(payload ->
                payload instanceof icu.tianqingyuluo.onlineim.pojo.dto.response.MessageAckPayload ack
                        && existing.getId().equals(ack.getMessageId())
                        && existing.getClientMessageId().equals(ack.getClientMessageId())));
    }

    @Test
    void rejectsConversationTargetMismatch() {
        assertFalse(sender.publishMessage(session,
                "{\"conversation_id\":\"conv_usr_receiverusr_sender\",\"receiver_id\":\"usr_other\","
                        + "\"message_type\":\"text\",\"content\":\"hello\",\"client_message_id\":\"client_1\"}"));

        verifyNoInteractions(messageRepository, redisStreamService, frameSender);
    }

    @Test
    void rejectsNonFriendBeforePersistence() {
        when(friendService.areFriends("usr_sender", "usr_receiver")).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> sender.publishMessage(session,
                "{\"conversation_id\":\"conv_usr_receiverusr_sender\",\"receiver_id\":\"usr_receiver\","
                        + "\"message_type\":\"text\",\"content\":\"hello\",\"client_message_id\":\"client_1\"}"));

        verify(messageRepository, never()).save(any());
        verifyNoInteractions(redisStreamService, frameSender);
    }

    @Test
    void rejectsNonFriendWhenConversationDoesNotExist() {
        when(friendService.areFriends("usr_sender", "usr_stranger")).thenReturn(false);
        when(conversationRepository.findByConversationIdAndUserId("conv_missing", "usr_sender"))
                .thenReturn(null);

        assertThrows(ForbiddenException.class, () -> sender.publishMessage(session,
                "{\"conversation_id\":\"conv_missing\",\"receiver_id\":\"usr_stranger\","
                        + "\"message_type\":\"text\",\"content\":\"hello\",\"client_message_id\":\"client_1\"}"));

        verify(conversationRepository, never()).findByConversationIdAndUserId(any(), any());
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(redisStreamService, frameSender);
    }

    @Test
    void rejectsConversationIdThatDoesNotBelongToSenderAndReceiver() {
        when(conversationRepository.findByConversationIdAndUserId("conv_usr_otherusr_sender", "usr_sender"))
                .thenReturn(Conversation.builder().conversationId("conv_usr_otherusr_sender")
                        .userId("usr_sender").targetId("usr_other").conversationType("private").build());

        assertFalse(sender.publishMessage(session,
                "{\"conversation_id\":\"conv_usr_otherusr_sender\",\"receiver_id\":\"usr_receiver\","
                        + "\"message_type\":\"text\",\"content\":\"hello\",\"client_message_id\":\"client_x\"}"));

        verify(messageRepository, never()).save(any());
        verifyNoInteractions(conversationRecords);
    }

    @Test
    void imageMessageContentIsReplacedByServerSideMediaMetadata() {
        when(chatMediaService.resolveForMessage("usr_sender", "image", "upl_00000000000000000000000000000001"))
                .thenReturn(new ChatMediaService.ResolvedMedia("{\"file_id\":\"upl_00000000000000000000000000000001\",\"url\":\"/onlineim/chats/images/x.png\"}",
                        java.util.Map.of("file_name", "x.png")));

        assertTrue(sender.publishMessage(session,
                "{\"conversation_id\":\"conv_usr_receiverusr_sender\",\"receiver_id\":\"usr_receiver\","
                        + "\"message_type\":\"image\",\"content\":\"upl_00000000000000000000000000000001\",\"client_message_id\":\"client_img\"}"));

        verify(messageRepository).save(argThat(message -> message.getContent().contains("/onlineim/chats/images/x.png")
                && "x.png".equals(message.getExt().get("file_name"))));
    }

    @Test
    void mediaRejectionIsPropagatedAsErrorAndNothingIsSaved() {
        when(chatMediaService.resolveForMessage(any(), any(), any()))
                .thenThrow(icu.tianqingyuluo.onlineim.exception.ChatMediaException.badRequest("文件不存在或不属于当前用户"));

        assertThrows(icu.tianqingyuluo.onlineim.exception.ChatMediaException.class, () -> sender.publishMessage(session,
                "{\"conversation_id\":\"conv_usr_receiverusr_sender\",\"receiver_id\":\"usr_receiver\","
                        + "\"message_type\":\"file\",\"content\":\"upl_x\",\"client_message_id\":\"client_f\"}"));
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(redisStreamService);
    }
}
