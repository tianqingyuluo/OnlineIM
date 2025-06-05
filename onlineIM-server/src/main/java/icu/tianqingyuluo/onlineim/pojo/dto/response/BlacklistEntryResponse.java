package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 黑名单条目响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistEntryResponse {
    
    /**
     * 用户ID
     */
    @JsonProperty("user_id")
    private String userID;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像URL
     */
    @JsonProperty("avatar_url")
    private String avatarUrl;
    
    /**
     * 添加时间
     */
    @JsonProperty("added_at")
    private String addedAt;
} 