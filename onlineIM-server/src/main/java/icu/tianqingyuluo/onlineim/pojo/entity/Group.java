package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群组信息实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    private String id;             // 群组ID，格式为：grp_+UUID
    private String name;           // 群组名称
    private String avatar;         // 群头像URL
    private String description;    // 群组描述
    private String ownerId;        // 群主ID，格式为：usr_+UUID
    private Integer maxMembers;    // 最大成员数
    private Integer currentMembers; // 当前成员数
    private Integer joinType;      // 加群方式：0-需要验证，1-无需验证，2-禁止加入
    private Integer muteType;      // 禁言类型：0-不禁言，1-全员禁言（除群主和管理员），2-仅群主和管理员可发言
    private Integer status;        // 群组状态：0-已解散，1-正常
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
} 