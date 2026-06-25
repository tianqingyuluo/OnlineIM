package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.RecallLog;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息撤回处理器
 * 处理类型为 RECALL_MESSAGE 的消息
 */
@Slf4j
@Component
public class RecallMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final GroupMemberService groupMemberService;
    private final LocalSessionRegistry sessionRegistry;

    @Autowired
    public RecallMessageReceiver(ObjectMapper objectMapper, MessageService messageService,
                                 GroupMemberService groupMemberService, LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.groupMemberService = groupMemberService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String getSupportedMessageType() {
        return "RECALL_MESSAGE";
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }

        log.info("处理消息撤回: sender={}, message={}", event.getSenderID(), event.getMessage());
        
        try {
            // 解析消息内容
            JsonNode messageNode = objectMapper.readTree(event.getMessage());
            String messageId = messageNode.has("message_id") ? messageNode.get("message_id").asText() : null;
            String conversationId = messageNode.has("conversation_id") ? messageNode.get("conversation_id").asText() : null;
            
            if (messageId == null || conversationId == null) {
                log.error("撤回消息缺少必要参数: message_id={}, conversation_id={}", messageId, conversationId);
                return false;
            }
            
            // 调用消息服务撤回消息
            boolean success = messageService.recallMessage(messageId, event.getSenderID());
            
            if (!success) {
                log.error("撤回消息失败: message_id={}, sender={}", messageId, event.getSenderID());
                return false;
            }
            
            // 创建响应消息
            Map<String, Object> response = new HashMap<>();
            response.put("type", "RECALL_MESSAGE_RESPONSE");
            response.put("status", "success");
            response.put("message_id", messageId);
            response.put("conversation_id", conversationId);
            response.put("timestamp", System.currentTimeMillis());
            
            // 向发送者发送响应（确认消息已撤回）
            WebSocketSession senderSession = sessionRegistry.getByUserId(event.getSenderID());
            if (senderSession != null && senderSession.isActive()) {
                String responseJson = objectMapper.writeValueAsString(response);
                senderSession.sendMessage(responseJson);
                log.debug("已向发送者 {} 发送撤回确认", event.getSenderID());
            }
            
            // 判断是私聊还是群聊
            boolean isGroupChat = conversationId.startsWith("grp_");
            
            // 获取需要通知的用户列表
            List<String> notifyUsers;
            if (isGroupChat) {
                // 群聊消息，通知群组所有成员
                notifyUsers = groupMemberService
                        .getGroupMembers(conversationId)
                        .stream().map(GroupMemberResponse::getUserInfo)
                        .map(UserBriefResponse::getUserId)
                        .toList();
            } else {
                // 私聊消息，通知对方
                String[] userIds = conversationId.replace("conv_", "").split("_");
                notifyUsers = List.of(userIds[0].equals(event.getSenderID()) ? userIds[1] : userIds[0]);
            }
            
            // 向接收者发送消息撤回通知
            Map<String, Object> notifyResponse = new HashMap<>();
            notifyResponse.put("type", "MESSAGE_RECALLED");
            notifyResponse.put("message_id", messageId);
            notifyResponse.put("conversation_id", conversationId);
            notifyResponse.put("recall_user_id", event.getSenderID());
            notifyResponse.put("timestamp", System.currentTimeMillis());
            
            String notifyResponseJson = objectMapper.writeValueAsString(notifyResponse);
            
            for (String userId : notifyUsers) {
                if (userId.equals(event.getSenderID())) {
                    continue; // 跳过发送者自己
                }
                
                WebSocketSession session = sessionRegistry.getByUserId(userId);
                if (session != null && session.isActive()) {
                    session.sendMessage(notifyResponseJson);
                    log.debug("已向用户 {} 发送消息撤回通知", userId);
                }
            }
            
            // 创建撤回日志
            RecallLog recallLog = new RecallLog();
            recallLog.setId("recall_" + System.currentTimeMillis());
            recallLog.setMessageId(messageId);
            recallLog.setConversationId(conversationId);
            recallLog.setOperatorId(event.getSenderID());
            recallLog.setRecallTime(new Date());
            recallLog.setSeqId(IdUtil.getSnowflakeNextIdStr());
            
            // 保存撤回日志
            messageService.saveRecallLog(recallLog);
            
            return true;
        } catch (Exception e) {
            log.error("处理消息撤回异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
