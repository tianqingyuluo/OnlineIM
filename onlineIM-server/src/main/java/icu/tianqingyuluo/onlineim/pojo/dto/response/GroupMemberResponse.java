package icu.tianqingyuluo.onlineim.pojo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群组成员响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {
    
    /**
     * 用户信息
     */
    private UserBriefResponse userInfo;
    
    /**
     * 角色：owner, admin, member
     */
    private String role;
    
    /**
     * 群昵称
     */
    private String groupNickname;
    
    /**
     * 是否被禁言
     */
    private Boolean isMuted;
    
    /**
     * 禁言截止时间
     */
    private String muteEndTime;
    
    /**
     * 加入时间
     */
    private String joinedAt;
} 