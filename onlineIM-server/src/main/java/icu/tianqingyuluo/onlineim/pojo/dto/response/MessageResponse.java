package icu.tianqingyuluo.onlineim.pojo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 消息响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    
    /**
     * 消息ID
     */
    private String messageId;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 发送者信息
     */
    private UserBriefResponse senderInfo;
    
    /**
     * 消息类型：text, image, file, audio, video
     */
    private String messageType;
    
    /**
     * 消息内容
     */
    private Object content;
    
    /**
     * 提及的用户ID列表
     */
    private List<String> mentionedUserIds;
    
    /**
     * 消息状态：sending, delivered, read, recalled
     */
    private String status;
    
    /**
     * 是否已撤回
     */
    private Boolean isRecalled;
    
    /**
     * 发送时间
     */
    private String timestamp;
    
    /**
     * 客户端消息ID
     */
    private String clientMessageId;
} 