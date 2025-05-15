package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友信息响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendResponse {
    
    /**
     * 好友关系ID
     */
    @JsonProperty("friendship_id")
    private String friendshipID;
    
    /**
     * 好友用户ID
     */
    @JsonProperty("user_id")
    private String userID;
    
    /**
     * 好友用户名
     */
    private String username;
    
    /**
     * 好友昵称
     */
    private String nickname;
    
    /**
     * 好友头像URL
     */
    @JsonProperty("avatar_url")
    private String avatarUrl;
    
    /**
     * 好友备注
     */
    private String remark;
    
    /**
     * 好友分组ID
     */
    @JsonProperty("friend_group_id")
    private String friendGroupID;
    
    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    private String createdAt;
} 