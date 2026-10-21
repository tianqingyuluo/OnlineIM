package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.GroupMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.util.MentionUtil;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.registry.SessionDelivery;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 群聊消息处理器：把 Redis Stream 中已落库的群消息（GROUP_MESSAGE）推送给本节点在线的群成员。
 *
 * <p>消息的落库、@提及清洗（只保留当前群成员）都在发送端完成；这里只负责投递。
 * 每个接收者的帧额外带顶层 {@code at_me}（是否 @ 了这位接收者，含 @所有人）和 {@code at_all}。</p>
 */
@Slf4j
@Component
public class GroupMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final LocalSessionRegistry sessionRegistry;

    @Autowired
    public GroupMessageReceiver(ObjectMapper objectMapper, MessageService messageService,
                                LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
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
            GroupMessage groupMessage = objectMapper.readValue(event.getMessage(), GroupMessage.class);
            if (groupMessage.getGroupId() == null) {
                log.error("群聊消息缺少群组ID");
                return false;
            }

            MessageResponse messageResponse = messageService.convertGroupMessageToResponse(groupMessage);
            List<String> atUsers = groupMessage.getAtUsers() == null ? List.of() : groupMessage.getAtUsers();
            boolean atAll = MentionUtil.mentionsAll(atUsers);

            // 每个成员的全部在线设备都收到，包括发送者自己的其他设备（发送端按 client_message_id 合并）
            for (String memberId : event.getReceiverIDs()) {
                if (memberId == null) {
                    continue;
                }
                List<WebSocketSession> sessions = SessionDelivery.of(sessionRegistry, memberId);
                if (sessions.isEmpty()) {
                    continue;
                }
                boolean atMe = atAll || atUsers.contains(memberId);
                for (WebSocketSession memberSession : sessions) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("type", "GROUP_MESSAGE_RESPONSE");
                    response.put("message", messageResponse);
                    response.put("at_me", atMe);
                    response.put("at_all", atAll);
                    memberSession.sendMessage(objectMapper.writeValueAsString(response));
                }
                log.debug("已向群组成员 {} 的 {} 台设备发送新消息", memberId, sessions.size());
            }

            return true;
        } catch (Exception e) {
            log.error("处理群聊消息异常: {}", e.getMessage(), e);
            return false;
        }
    }
}
