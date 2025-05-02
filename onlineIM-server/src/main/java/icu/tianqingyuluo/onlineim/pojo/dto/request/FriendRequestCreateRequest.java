package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送好友请求请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequestCreateRequest {
    
    /**
     * 目标用户ID
     */
    @NotBlank(message = "目标用户ID不能为空")
    private String targetUserId;
    
    /**
     * 验证消息
     */
    @Size(max = 200, message = "验证消息最多200个字符")
    private String message;
} 