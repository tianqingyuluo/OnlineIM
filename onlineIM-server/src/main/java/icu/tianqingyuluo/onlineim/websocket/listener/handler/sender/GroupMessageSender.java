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
import icu.tianqingyuluo.onlineim.exception.ChatMediaException;
import icu.tianqingyuluo.onlineim.service.ChatMediaService;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageReplyService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.util.MentionUtil;
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
    private final ConversationRecordService conversationRecords;
    private final ChatMediaService chatMediaService;

    public GroupMessageSender(ObjectMapper objectMapper,
                              GroupMessageRepository groupMessageRepository,
                              RedisStreamService redisStreamService,
                              GroupMemberService groupMemberService,
                              WebSocketFrameSender frameSender,
                              MessageReplyService messageReplyService,
                              ConversationRecordService conversationRecords,
                              ChatMediaService chatMediaService) {
        this.objectMapper = objectMapper;
        this.groupMessageRepository = groupMessageRepository;
        this.redisStreamService = redisStreamService;
        this.groupMemberService = groupMemberService;
        this.frameSender = frameSender;
        this.messageReplyService = messageReplyService;
        this.conversationRecords = conversationRecords;
        this.chatMediaService = chatMediaService;
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
            if (ChatMediaService.isMediaMessage(request.getMessageType())) {
                // 图片/文件消息：content 只携带 file_id，URL、大小、尺寸等由服务端按上传记录补全
                ChatMediaService.ResolvedMedia media = chatMediaService.resolveForMessage(
                        senderId, request.getMessageType(), request.getContent());
                groupMessage.setContent(media.content());
                groupMessage.setExt(media.ext());
            } else {
                groupMessage.setContent(request.getContent());
            }
            groupMessage.setReplyTo(replyTo);
            groupMessage.setContentRevision(1);
            groupMessage.setStatus(0);
            groupMessage.setSeqId(IdUtil.getSnowflakeNextIdStr());
            groupMessage.setClientMessageId(request.getClientMessageId());
            java.util.List<String> memberIds = groupMemberService.getGroupMembers(groupMessage.getGroupId())
                    .stream()
                    .map(GroupMemberResponse::getUserInfo)
                    .filter(java.util.Objects::nonNull)
                    .map(UserBriefResponse::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            // 只保留当前群成员（不含自己）的 @，非法项丢弃
            groupMessage.setAtUsers(MentionUtil.sanitize(
                    request.getMessageType(), senderId, request.getAtUsers(), memberIds));
            groupMessage.setTimestamp(new Date());
            groupMessage.setCreatedAt(new Date());
            groupMessage.setUpdatedAt(new Date());

            groupMessageRepository.save(groupMessage);
            conversationRecords.onGroupMessage(groupMessage, memberIds);
            redisStreamService.publishGroupMessage(
                    "GROUP_MESSAGE",
                    senderId,
                    objectMapper.writeValueAsString(groupMessage),
                    memberIds);

            sendAck(session, groupMessage);
            return true;
        } catch (ReplyTargetUnavailableException | ChatMediaException ex) {
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
