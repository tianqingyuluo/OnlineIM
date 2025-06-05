package icu.tianqingyuluo.onlineim.websocket.processor;

import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver.MessageReceiverHandler;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.MessageTypeReceiverRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * WebSocket 消息处理器
 * 负责接收 WebSocketMessageEvent 事件并分发给对应的消息类型处理器
 */
@Slf4j
@Component
public class WebSocketMessageProcessor {

    private final MessageTypeReceiverRegistry receiverRegistry;

    @Autowired
    public WebSocketMessageProcessor(MessageTypeReceiverRegistry receiverRegistry) {
        this.receiverRegistry = receiverRegistry;
    }

    /**
     * 处理 WebSocketMessageEvent 事件
     * 根据消息类型分发给对应的处理器
     * @param event WebSocket 消息事件
     */
    @EventListener
    public void processMessage(WebSocketMessageEvent event) {
        String messageType = event.getType();
        log.debug("收到 WebSocket 消息事件: type={}, sender={}", messageType, event.getSenderID());
        
        // 获取对应的消息处理器
        MessageReceiverHandler handler = receiverRegistry.getHandler(messageType);
        
        if (handler == null) {
            log.warn("未找到消息类型 {} 的处理器", messageType);
            return;
        }
        
        try {
            // 调用处理器处理消息
            boolean result = handler.handleMessage(event);
            if (result) {
                log.debug("消息处理成功: type={}, sender={}", messageType, event.getSenderID());
            } else {
                log.warn("消息处理失败: type={}, sender={}", messageType, event.getSenderID());
            }
        } catch (Exception e) {
            log.error("处理消息异常: type={}, sender={}, error={}", 
                    messageType, event.getSenderID(), e.getMessage(), e);
        }
    }
}
