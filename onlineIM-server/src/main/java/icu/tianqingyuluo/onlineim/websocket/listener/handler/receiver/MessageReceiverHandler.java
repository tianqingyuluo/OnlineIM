package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;

/**
 * 消息类型处理器接口
 * 不同类型的消息处理器需要实现此接口
 */
public interface MessageReceiverHandler {
    
    /**
     * 获取处理器支持的消息类型
     * @return 消息类型
     */
    String getSupportedMessageType();
    
    /**
     * 处理消息
     * @param event WebSocket消息事件
     * @return 处理结果，true表示处理成功，false表示处理失败
     */
    boolean handleMessage(WebSocketMessageEvent event);
}
