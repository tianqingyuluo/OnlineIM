package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "目标用户ID不能为空")
    @JsonProperty("receiver_id")
    private String targetUserId;
    
    /**
     * 验证消息
     */
    @Size(max = 200, message = "验证消息最多200个字符")
    private String message;
} 