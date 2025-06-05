package icu.tianqingyuluo.onlineim.websocket.listener.handler;

import icu.tianqingyuluo.onlineim.websocket.listener.handler.sender.MessageSenderHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MessageTypeSenderRegistry {

    private final Map<String, MessageSenderHandler> senderMap = new HashMap<>();
    private final List<MessageSenderHandler> handlers;

    public MessageTypeSenderRegistry(List<MessageSenderHandler> handlers) {
        this.handlers = handlers;
    }

    @PostConstruct
    public void init() {
        for (MessageSenderHandler handler : handlers) {
            String messageType = handler.getSupportedMessageType();
            senderMap.put(messageType, handler);
            log.info("注册消息发送处理器: type={}, handler={}", messageType, handler.getClass().getSimpleName());
        }

    }

    public MessageSenderHandler getHandler(String messageType) {
        return senderMap.get(messageType);
    }

    public boolean supportMessageType(String messageType) {
        return senderMap.containsKey(messageType);
    }
}
