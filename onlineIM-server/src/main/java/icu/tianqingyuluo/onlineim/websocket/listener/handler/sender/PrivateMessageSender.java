package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.PrivateMessageRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageAckPayload;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketFrameSender;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class PrivateMessageSender implements MessageSenderHandler {
    private final ObjectMapper objectMapper;
    private final PrivateMessageRepository privateMessageRepository;
    private final ConversationRepository conversationRepository;
    private final RedisStreamService redisStreamService;
    private final WebSocketFrameSender frameSender;

    public PrivateMessageSender(ObjectMapper objectMapper,
                                PrivateMessageRepository privateMessageRepository,
                                ConversationRepository conversationRepository,
                                RedisStreamService redisStreamService,
                                WebSocketFrameSender frameSender) {
        this.objectMapper = objectMapper;
        this.privateMessageRepository = privateMessageRepository;
        this.conversationRepository = conversationRepository;
        this.redisStreamService = redisStreamService;
        this.frameSender = frameSender;
    }

    @Override
    public String getSupportedMessageType() {
        return "PRIVATE_MESSAGE_REQUEST";
    }

    @Override
    public boolean publishMessage(WebSocketSession session, String message) {
        if (session == null || session.getUserId() == null) {
            return false;
        }
        try {
            PrivateMessageRequest request = objectMapper.readValue(message, PrivateMessageRequest.class);
            if (isBlank(request.getConversationId()) || isBlank(request.getReceiverId())
                    || isBlank(request.getMessageType()) || request.getContent() == null
                    || isBlank(request.getClientMessageId())
                    || session.getUserId().equals(request.getReceiverId())
                    || !isConversationParticipant(request.getConversationId(), session.getUserId(), request.getReceiverId())) {
                return false;
            }

            String senderId = session.getUserId();
            PrivateMessage existing = privateMessageRepository.findBySenderIdAndClientMessageId(
                    senderId, request.getClientMessageId());
            if (existing != null) {
                sendAck(session, existing);
                return true;
            }

            PrivateMessage privateMessage = new PrivateMessage();
            privateMessage.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
            privateMessage.setConversationId(request.getConversationId());
            privateMessage.setSenderId(senderId);
            privateMessage.setReceiverId(request.getReceiverId());
            privateMessage.setMessageType(request.getMessageType());
            privateMessage.setContent(request.getContent());
            privateMessage.setStatus(0);
            privateMessage.setClientMessageId(request.getClientMessageId());
            privateMessage.setSeqId(IdUtil.getSnowflakeNextIdStr());
            privateMessage.setTimestamp(new Date());
            privateMessage.setCreatedAt(new Date());
            privateMessage.setUpdatedAt(new Date());

            privateMessageRepository.save(privateMessage);
            redisStreamService.publishPrivateMessage(
                    "PRIVATE_MESSAGE",
                    senderId,
                    objectMapper.writeValueAsString(privateMessage),
                    request.getReceiverId());

            sendAck(session, privateMessage);
            return true;
        } catch (Exception ex) {
            log.warn("处理私聊消息失败: userId={}, error={}", session.getUserId(), ex.getMessage());
            return false;
        }
    }

    private void sendAck(WebSocketSession session, PrivateMessage message) {
        frameSender.send(session, "MESSAGE_ACK", MessageAckPayload.builder()
                .clientMessageId(message.getClientMessageId())
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .seqId(message.getSeqId())
                .deliveryState("sent")
                .serverTime(System.currentTimeMillis())
                .build());
    }


    private boolean isConversationParticipant(String conversationId, String senderId, String receiverId) {
        Conversation conversation = conversationRepository.findByIdAndUserIDOrTargetId(conversationId, senderId);
        if (conversation == null || !"private".equals(conversation.getConversationType())) {
            return false;
        }
        return (senderId.equals(conversation.getUserId()) && receiverId.equals(conversation.getTargetId()))
                || (senderId.equals(conversation.getTargetId()) && receiverId.equals(conversation.getUserId()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
