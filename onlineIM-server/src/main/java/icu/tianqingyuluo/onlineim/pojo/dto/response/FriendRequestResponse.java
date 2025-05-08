package icu.tianqingyuluo.onlineim.pojo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友请求响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestResponse {
    
    /**
     * 请求ID
     */
    private String requestID;
    
    /**
     * 发送者信息
     */
    private UserBriefResponse senderInfo;
    
    /**
     * 验证消息
     */
    private String message;
    
    /**
     * 状态：pending, accepted, rejected
     */
    private String status;
    
    /**
     * 创建时间
     */
    private String createdAt;
} 