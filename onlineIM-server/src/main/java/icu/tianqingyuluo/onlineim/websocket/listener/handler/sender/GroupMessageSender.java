package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.GroupMessageRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.repository.GroupMessageRepository;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import lombok.SneakyThrows;
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

    public GroupMessageSender(ObjectMapper objectMapper, GroupMessageRepository groupMessageRepository, RedisStreamService redisStreamService, GroupMemberService groupMemberService) {
        this.objectMapper = objectMapper;
        this.groupMessageRepository = groupMessageRepository;
        this.redisStreamService = redisStreamService;
        this.groupMemberService = groupMemberService;
    }

    @Override
    public String getSupportedMessageType() {
        return "GROUP_MESSAGE_REQUEST"; // 假设请求类型为 GROUP_MESSAGE_REQUEST
    }

    @SneakyThrows
    @Override
    public boolean publishMessage(String message) {

        // 假设 GroupMessageRequest 包含 groupId, senderId, messageType, content, atUsers
        GroupMessageRequest request = objectMapper.readValue(message, GroupMessageRequest.class);

        // 构建GroupMessage对象
        GroupMessage groupMessage = new GroupMessage();
        groupMessage.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
        groupMessage.setGroupId(request.getGroupId());
        groupMessage.setSenderId(request.getSenderId());
        groupMessage.setMessageType(request.getMessageType());
        groupMessage.setContent(request.getContent());
        groupMessage.setStatus(0); // 默认发送中
        groupMessage.setSeqId(IdUtil.getSnowflakeNextIdStr());
        groupMessage.setClientMessageId(request.getClientMessageId());
        groupMessage.setAtUsers(request.getAtUsers());
        groupMessage.setTimestamp(new Date());
        groupMessage.setCreatedAt(new Date());
        groupMessage.setUpdatedAt(new Date());

        groupMessageRepository.save(groupMessage); // 在mongodb存储

        // 推送到redis流
        // 注意：群聊消息需要将消息发送给群组内的所有成员，这里需要获取群组成员列表作为receiverIDs
        // 由于当前没有获取群组成员的逻辑，这里暂时将groupId作为receiverID，实际应用中需要修改
        redisStreamService.publishGroupMessage(
                "GROUP_MESSAGE", // Stream事件类型
                request.getSenderId(),
                objectMapper.writeValueAsString(groupMessage),
                groupMemberService
                        .getGroupMembers(groupMessage.getGroupId())
                        .stream()
                        .map(GroupMemberResponse::getUserInfo)
                        .map(UserBriefResponse::getUserId)
                        .toList() // 接收者ID列表
        );

        return true;
    }
}
