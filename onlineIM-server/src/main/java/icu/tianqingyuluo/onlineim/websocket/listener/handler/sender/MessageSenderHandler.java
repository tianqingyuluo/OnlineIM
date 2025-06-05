package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;

public interface MessageSenderHandler {

    String getSupportedMessageType();

    boolean publishMessage(String message);
}
