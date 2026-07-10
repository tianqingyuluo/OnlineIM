package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;

public class NoticeMessageSender implements MessageSenderHandler {
    @Override
    public String getSupportedMessageType() {
        return "NOTICE_MESSAGE_REQUEST";
    }

    @Override
    public boolean publishMessage(WebSocketSession session, String message) {
        return false;
    }
}
