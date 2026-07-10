package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 群聊消息处理器
 * 处理类型为 GROUP_MESSAGE 的消息
 */
@Slf4j
@Component
public class GroupMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final GroupMemberService groupMemberService;
    private final LocalSessionRegistry sessionRegistry;

    @Autowired
    public GroupMessageReceiver(ObjectMapper objectMapper, MessageService messageService,
                                GroupMemberService groupMemberService, LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.groupMemberService = groupMemberService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String getSupportedMessageType() {
        return "GROUP_MESSAGE";
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }

        log.info("处理群聊消息: sender={}, message={}", event.getSenderID(), event.getMessage());
        
        try {
            // 解析消息内容
            GroupMessage groupMessage = objectMapper.readValue(event.getMessage(), GroupMessage.class);
            
            if (groupMessage.getGroupId() == null) {
                log.error("群聊消息缺少群组ID");
                return false;
            }
            
//            // 处理@用户
//            List<String> atUsers = new ArrayList<>();
//            if (messageNode.has("at_users") && messageNode.get("at_users").isArray()) {
//                JsonNode atUsersNode = messageNode.get("at_users");
//                for (JsonNode atUser : atUsersNode) {
//                    atUsers.add(atUser.asText());
//                }
//            }
//
//            // 创建群聊消息对象
//            GroupMessage groupMessage = new GroupMessage();
//            groupMessage.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
//            groupMessage.setGroupId(groupId);
//            groupMessage.setSenderId(event.getSenderID());
//            groupMessage.setMessageType("text");
//            groupMessage.setContent(content);
//            groupMessage.setStatus(1); // 已送达
//            groupMessage.setSeqId(IdUtil.getSnowflakeNextIdStr());
//            groupMessage.setAtUsers(atUsers);
//            groupMessage.setTimestamp(new Date());
//            groupMessage.setCreatedAt(new Date());
//            groupMessage.setUpdatedAt(new Date());

//            // 将消息保存到数据库
//            messageService.saveGroupMessage(groupMessage);
            
            // 将消息转换为响应对象
            MessageResponse messageResponse = messageService.convertGroupMessageToResponse(groupMessage);
            
            // 创建响应消息
            Map<String, Object> response = new HashMap<>();
            response.put("type", "GROUP_MESSAGE_RESPONSE");
            response.put("message", messageResponse);
            
//            // 获取群组成员列表
//            List<String> groupMembers = groupMemberService
//                    .getGroupMembers(groupMessage.getGroupId())
//                    .stream()
//                    .map(GroupMemberResponse::getUserInfo)
//                    .map(UserBriefResponse::getUserId)
//                    .toList();
            
            // 向群组成员发送消息（排除发送者自己）
            for (String memberId : event.getReceiverIDs()) {
                if (memberId.equals(event.getSenderID())) {
                    continue; // 跳过发送者自己
                }
                
                WebSocketSession memberSession = sessionRegistry.getByUserId(memberId);
                if (memberSession != null && memberSession.isActive()) {
                    // 修改响应类型为新消息
                    response.put("type", "GROUP_MESSAGE_RESPONSE");
                    String responseJson = objectMapper.writeValueAsString(response);
                    memberSession.sendMessage(responseJson);
                    log.debug("已向群组成员 {} 发送新消息", memberId);
                }
            }
            
            return true;
        } catch (Exception e) {
            log.error("处理群聊消息异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
