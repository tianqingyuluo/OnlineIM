package icu.tianqingyuluo.onlineim.pojo.dto.response;

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
    private String friendshipId;
    
    /**
     * 好友用户ID
     */
    private String userId;
    
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
    private String avatarUrl;
    
    /**
     * 好友备注
     */
    private String remark;
    
    /**
     * 好友分组ID
     */
    private String friendGroupId;
    
    /**
     * 在线状态
     */
    private String onlineStatus;
    
    /**
     * 创建时间
     */
    private String createdAt;
} 