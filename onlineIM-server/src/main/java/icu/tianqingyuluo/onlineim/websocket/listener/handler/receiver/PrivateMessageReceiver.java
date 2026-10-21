package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.registry.SessionDelivery;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 私聊消息处理器
 * 处理类型为 PRIVATE_MESSAGE 的消息
 */
@Slf4j
@Component
public class PrivateMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final LocalSessionRegistry sessionRegistry;

    @Autowired
    public PrivateMessageReceiver(ObjectMapper objectMapper, MessageService messageService,
                                  LocalSessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public String getSupportedMessageType() {
        return "PRIVATE_MESSAGE";
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }

        log.info("处理私聊消息: sender={}, message={}", event.getSenderID(), event.getMessage());
        
        try {
            // 解析消息内容
            PrivateMessage privateMessage = objectMapper.readValue(event.getMessage(), PrivateMessage.class);

            
            if (privateMessage.getReceiverId() == null) {
                log.error("私聊消息缺少目标用户ID");
                return false;
            }
            
//            // 创建私聊消息对象
//            PrivateMessage privateMessage = new PrivateMessage();
//            privateMessage.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
//            privateMessage.setConversationId("conv_" + event.getSenderID() + "_" + targetUserId);
//            privateMessage.setSenderId(event.getSenderID());
//            privateMessage.setReceiverId(targetUserId);
//            privateMessage.setMessageType("text");
//            privateMessage.setContent(content);
//            privateMessage.setStatus(1); // 已送达
//            privateMessage.setSeqId(IdUtil.getSnowflakeNextIdStr());
//            privateMessage.setTimestamp(new Date());
//            privateMessage.setCreatedAt(new Date());
//            privateMessage.setUpdatedAt(new Date());
//
//            // 将消息保存到数据库
//            messageService.savePrivateMessage(privateMessage);
            
            // 将消息转换为响应对象
            MessageResponse messageResponse = messageService.convertPrivateMessageToResponse(privateMessage);
            
            // 创建响应消息
            Map<String, Object> response = new HashMap<>();
            response.put("type", "PRIVATE_MESSAGE_RESPONSE");
            response.put("message", messageResponse);
            
            // 接收者的每一台在线设备都收到；发送者的其他设备也收到，这样自己发出的消息会出现在同时在线的另一端
            String responseJson = objectMapper.writeValueAsString(response);
            deliver(privateMessage.getReceiverId(), responseJson);
            if (privateMessage.getSenderId() != null
                    && !privateMessage.getSenderId().equals(privateMessage.getReceiverId())) {
                deliver(privateMessage.getSenderId(), responseJson);
            }
            log.debug("已向接收者 {} 及发送者的其他设备发送新消息", privateMessage.getReceiverId());
            
            return true;
        } catch (Exception e) {
            log.error("处理私聊消息异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private void deliver(String userId, String responseJson) {
        for (WebSocketSession session : SessionDelivery.of(sessionRegistry, userId)) {
            session.sendMessage(responseJson);
        }
    }
}
