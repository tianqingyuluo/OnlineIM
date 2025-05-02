package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 群组信息请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupRequest {
    
    /**
     * 群组名称
     */
    private String name;
    
    /**
     * 群组头像URL
     */
    private String avatarUrl;
    
    /**
     * 群组描述
     */
    private String description;
    
    /**
     * 群组公告
     */
    private String announcement;
    
    /**
     * 初始成员ID列表
     */
    private List<String> initialMembers;
} 