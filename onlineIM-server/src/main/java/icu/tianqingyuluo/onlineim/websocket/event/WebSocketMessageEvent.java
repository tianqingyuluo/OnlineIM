package icu.tianqingyuluo.onlineim.websocket.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * WebSocket消息事件
 * 用于在Spring事件总线中传递消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessageEvent {
    /**
     * 消息类型
     */
    private String type;
    
    /**
     * 发送者ID
     */
    private String senderID;
    
    /**
     * 消息内容
     */
    private String message;
    
    /**
     * 接收者ID列表
     */
    private List<String> receiverIDs;
}
