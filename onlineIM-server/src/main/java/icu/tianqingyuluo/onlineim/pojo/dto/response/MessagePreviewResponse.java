package icu.tianqingyuluo.onlineim.pojo.dto.response;

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
    private String messageId;
    
    /**
     * 发送者昵称
     */
    private String senderNickname;
    
    /**
     * 内容预览
     */
    private String contentPreview;
    
    /**
     * 消息类型
     */
    private String messageType;
    
    /**
     * 时间戳
     */
    private String timestamp;
    
    /**
     * 是否已撤回
     */
    private Boolean isRecalled;
} 