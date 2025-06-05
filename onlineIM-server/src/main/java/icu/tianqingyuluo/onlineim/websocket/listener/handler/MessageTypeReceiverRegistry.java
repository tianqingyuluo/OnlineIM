package icu.tianqingyuluo.onlineim.websocket.listener.handler;

import icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver.MessageReceiverHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息类型处理器注册表
 * 用于注册和获取不同类型的消息处理器
 */
@Slf4j
@Component
public class MessageTypeReceiverRegistry {

    private final Map<String, MessageReceiverHandler> handlerMap = new HashMap<>();
    private final List<MessageReceiverHandler> handlers;

    @Autowired
    public MessageTypeReceiverRegistry(List<MessageReceiverHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * 初始化注册表
     * 将所有实现了 MessageReceiverHandler 接口的处理器注册到注册表中
     */
    @PostConstruct
    public void init() {
        for (MessageReceiverHandler handler : handlers) {
            String messageType = handler.getSupportedMessageType();
            handlerMap.put(messageType, handler);
            log.info("注册消息接收处理器: type={}, handler={}", messageType, handler.getClass().getSimpleName());
        }
    }

    /**
     * 获取指定类型的消息处理器
     * @param messageType 消息类型
     * @return 消息处理器，如果没有找到则返回 null
     */
    public MessageReceiverHandler getHandler(String messageType) {
        return handlerMap.get(messageType);
    }

    /**
     * 判断是否支持指定类型的消息
     * @param messageType 消息类型
     * @return 是否支持
     */
    public boolean supportsMessageType(String messageType) {
        return handlerMap.containsKey(messageType);
    }
}
