package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友请求请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestRequest {
    
    /**
     * 目标用户ID
     */
    private String targetUserId;
    
    /**
     * 验证消息
     */
    private String message;
} 