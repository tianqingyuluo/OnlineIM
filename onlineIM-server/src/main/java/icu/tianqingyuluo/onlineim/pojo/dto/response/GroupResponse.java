package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("group_id")
    private String groupID;
    
    /**
     * 群组名称
     */
    private String name;
    
    /**
     * 群主ID
     */
    @JsonProperty("owner_id")
    private String ownerID;
    
    /**
     * 群头像URL
     */
    @JsonProperty("avatar_url")
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
    @JsonProperty("member_count")
    private Integer memberCount;
    
    /**
     * 当前用户在群中的角色
     */
    @JsonProperty("my_role")
    private String myRole;
    
    /**
     * 创建时间
     */
    @JsonProperty("create_at")
    private String createdAt;
} 