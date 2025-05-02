package icu.tianqingyuluo.onlineim.pojo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群组信息响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    
    /**
     * 群组ID
     */
    private String groupId;
    
    /**
     * 群组名称
     */
    private String name;
    
    /**
     * 群主ID
     */
    private String ownerId;
    
    /**
     * 群头像URL
     */
    private String avatarUrl;
    
    /**
     * 群组描述
     */
    private String description;
    
    /**
     * 群公告
     */
    private String announcement;
    
    /**
     * 成员数量
     */
    private Integer memberCount;
    
    /**
     * 当前用户在群中的角色
     */
    private String myRole;
    
    /**
     * 创建时间
     */
    private String createdAt;
} 