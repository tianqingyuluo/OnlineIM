package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息预览响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePreviewResponse {
    
    /**
     * 消息ID
     */
    @JsonProperty("message_id")
    private String messageId;
    
    /**
     * 发送者昵称
     */
    @JsonProperty("sender_nickname")
    private String senderNickname;
    
    /**
     * 内容预览
     */
    @JsonProperty("content_preview")
    private String contentPreview;
    
    /**
     * 消息类型
     */
    @JsonProperty("message_type")
    private String messageType;
    
    /**
     * 时间戳
     */
    private String timestamp;
    
    /**
     * 是否已撤回
     */
    @JsonProperty("is_recalled")
    private Boolean isRecalled;
} 