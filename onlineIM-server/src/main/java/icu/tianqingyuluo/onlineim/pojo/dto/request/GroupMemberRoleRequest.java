package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群组成员角色请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberRoleRequest {
    
    /**
     * 角色：owner, admin, member
     */
    private String role;
} 