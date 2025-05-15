package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("user_info")
    private UserBriefResponse userInfo;
    
    /**
     * 角色：owner, admin, member
     */
    private String role;
    
    /**
     * 群昵称
     */
    @JsonProperty("group_nickname")
    private String groupNickname;
    
    /**
     * 是否被禁言
     */
    @JsonProperty("is_muted")
    private Boolean isMuted;
    
    /**
     * 禁言截止时间
     */
    @JsonProperty("mute_end_time")
    private String muteEndTime;
    
    /**
     * 加入时间
     */
    @JsonProperty("joined_at")
    private String joinedAt;
} 