package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

public class NoticeMessageSender implements MessageSenderHandler {
    @Override
    public String getSupportedMessageType() {
        return "NOTICE_MESSAGE_REQUEST";
    }

    @Override
    public boolean publishMessage(String message) {
        return false;
    }
}
