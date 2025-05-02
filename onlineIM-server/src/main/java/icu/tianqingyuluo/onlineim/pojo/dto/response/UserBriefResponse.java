package icu.tianqingyuluo.onlineim.pojo.dto.response;

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
    private String userId;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
} 