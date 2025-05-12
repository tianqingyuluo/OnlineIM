package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户简要信息响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefResponse {
    
    /**
     * 用户ID
     */
    @JsonProperty("user_id")
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像URL
     */
    @JsonProperty("avatar_url")
    private String avatarUrl;
} 