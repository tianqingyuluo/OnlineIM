package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ReplyTargetUnavailableException;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.document.MessageReplySnapshot;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.GroupMessageRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageAckPayload;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageReplyService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketFrameSender;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class GroupMessageSender implements MessageSenderHandler {
    private final ObjectMapper objectMapper;
    private final GroupMessageRepository groupMessageRepository;
    private final RedisStreamService redisStreamService;
    private final GroupMemberService groupMemberService;
    private final WebSocketFrameSender frameSender;
    private final MessageReplyService messageReplyService;

    public GroupMessageSender(ObjectMapper objectMapper,
                              GroupMessageRepository groupMessageRepository,
                              RedisStreamService redisStreamService,
                              GroupMemberService groupMemberService,
                              WebSocketFrameSender frameSender,
                              MessageReplyService messageReplyService) {
        this.objectMapper = objectMapper;
        this.groupMessageRepository = groupMessageRepository;
        this.redisStreamService = redisStreamService;
        this.groupMemberService = groupMemberService;
        this.frameSender = frameSender;
        this.messageReplyService = messageReplyService;
    }

    @Override
    public String getSupportedMessageType() {
        return "GROUP_MESSAGE_REQUEST";
    }

    @Override
    public boolean publishMessage(WebSocketSession session, String message) {
        if (session == null || session.getUserId() == null) {
            return false;
        }
        try {
            GroupMessageRequest request = objectMapper.readValue(message, GroupMessageRequest.class);
            if (isBlank(request.getGroupId()) || isBlank(request.getMessageType())
                    || request.getContent() == null || isBlank(request.getClientMessageId())
                    || !groupMemberService.isGroupMember(request.getGroupId(), session.getUserId())) {
                return false;
            }

            String senderId = session.getUserId();
            GroupMessage existing = groupMessageRepository.findBySenderIdAndClientMessageId(
                    senderId, request.getClientMessageId());
            if (existing != null) {
                sendAck(session, existing);
                return true;
            }

            MessageReplySnapshot replyTo = isBlank(request.getReplyToMessageId())
                    ? null
                    : messageReplyService.createSnapshot(
                            request.getGroupId(), request.getReplyToMessageId(), senderId);

            GroupMessage groupMessage = new GroupMessage();
            groupMessage.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
            groupMessage.setGroupId(request.getGroupId());
            groupMessage.setSenderId(senderId);
            groupMessage.setMessageType(request.getMessageType());
            groupMessage.setContent(request.getContent());
            groupMessage.setReplyTo(replyTo);
            groupMessage.setContentRevision(1);
            groupMessage.setStatus(0);
            groupMessage.setSeqId(IdUtil.getSnowflakeNextIdStr());
            groupMessage.setClientMessageId(request.getClientMessageId());
            groupMessage.setAtUsers(request.getAtUsers());
            groupMessage.setTimestamp(new Date());
            groupMessage.setCreatedAt(new Date());
            groupMessage.setUpdatedAt(new Date());

            groupMessageRepository.save(groupMessage);
            redisStreamService.publishGroupMessage(
                    "GROUP_MESSAGE",
                    senderId,
                    objectMapper.writeValueAsString(groupMessage),
                    groupMemberService.getGroupMembers(groupMessage.getGroupId())
                            .stream()
                            .map(GroupMemberResponse::getUserInfo)
                            .filter(java.util.Objects::nonNull)
                            .map(UserBriefResponse::getUserId)
                            .filter(java.util.Objects::nonNull)
                            .toList());

            sendAck(session, groupMessage);
            return true;
        } catch (ReplyTargetUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("处理群聊消息失败: userId={}, error={}", session.getUserId(), ex.getMessage());
            return false;
        }
    }

    private void sendAck(WebSocketSession session, GroupMessage message) {
        frameSender.send(session, "MESSAGE_ACK", MessageAckPayload.builder()
                .clientMessageId(message.getClientMessageId())
                .messageId(message.getId())
                .conversationId(message.getGroupId())
                .seqId(message.getSeqId())
                .deliveryState("sent")
                .serverTime(System.currentTimeMillis())
                .build());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
