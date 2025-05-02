package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群组成员实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {
    private String id;              // 记录ID，格式为：mem_+UUID
    private String groupId;         // 群组ID，格式为：grp_+UUID
    private String userId;          // 用户ID，格式为：usr_+UUID
    private String nickname;        // 群内昵称
    private Integer role;           // 角色：0-普通成员，1-管理员，2-群主
    private LocalDateTime muteEndTime; // 禁言结束时间
    private LocalDateTime joinTime;    // 加入时间
    private Integer status;         // 成员状态：0-已退出，1-正常
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
} 