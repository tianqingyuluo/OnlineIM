package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友请求请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestRequest {
    
    /**
     * 目标用户ID
     */
    @JsonProperty("receiver_id")
    private String targetUserId;
    
    /**
     * 验证消息
     */
    private String message;
} 