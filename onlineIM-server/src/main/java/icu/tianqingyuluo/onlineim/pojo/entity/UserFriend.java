package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友关系实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFriend {
    private String id;          // 关系ID，格式为：rel_+UUID
    private String userId;      // 用户ID，格式为：usr_+UUID
    private String friendId;    // 好友ID，格式为：usr_+UUID
    private String remark;      // 好友备注
    private String groupId;     // 好友分组ID，格式为：grp_+UUID
    private Integer status;     // 好友状态：0-已删除，1-正常，2-已拉黑
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
} 