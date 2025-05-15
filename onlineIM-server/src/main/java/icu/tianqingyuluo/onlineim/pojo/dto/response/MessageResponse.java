package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("message_id")
    private String messageId;
    
    /**
     * 会话ID
     */
    @JsonProperty("conversation_id")
    private String conversationId;
    
    /**
     * 发送者信息
     */
    @JsonProperty("sender_info")
    private UserBriefResponse senderInfo;
    
    /**
     * 消息类型：text, image, file, audio, video
     */
    @JsonProperty("message_type")
    private String messageType;
    
    /**
     * 消息内容
     */
    private Object content;
    
    /**
     * 提及的用户ID列表
     */
    @JsonProperty("mention_user_ids")
    private List<String> mentionedUserIds;
    
    /**
     * 消息状态：sending, delivered, read, recalled
     */
    private String status;
    
    /**
     * 是否已撤回
     */
    @JsonProperty("is_recalled")
    private Boolean isRecalled;
    
    /**
     * 发送时间
     */
    private String timestamp;
    
    /**
     * 客户端消息ID
     */
    @JsonProperty("client_message_id")
    private String clientMessageId;
} 