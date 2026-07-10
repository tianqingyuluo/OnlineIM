package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;

public interface MessageSenderHandler {

    String getSupportedMessageType();

    /**
     * 处理带认证上下文的客户端消息。用户身份必须从 session 获取，不能信任 payload。
     */
    boolean publishMessage(WebSocketSession session, String message);

    /**
     * 兼容旧调用方；新路由统一使用带 session 的重载。
     */
    default boolean publishMessage(String message) {
        return publishMessage(null, message);
    }
}
