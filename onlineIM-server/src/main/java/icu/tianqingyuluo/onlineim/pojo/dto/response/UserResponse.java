package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    
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
    
    /**
     * 个性签名
     */
    private String signature;
    
    /**
     * 地区
     */
    private String region;
    
    /**
     * 性别：male, female
     */
    private String gender;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 是否在线
     */
    private Boolean online;
    
    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    private String createdAt;
} 