package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 消息请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 消息类型：text, image, file, audio, video
     */
    private String messageType;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 提及的用户ID列表
     */
    private List<String> mentionedUserIDs;
    
    /**
     * 客户端消息ID（用于消息去重）
     */
    private String clientMessageId;
} 