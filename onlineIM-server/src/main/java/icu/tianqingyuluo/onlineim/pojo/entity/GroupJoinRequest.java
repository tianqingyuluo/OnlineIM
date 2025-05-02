package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群组加入请求实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupJoinRequest {
    private String id;          // 请求ID，格式为：jrq_+UUID
    private String groupId;     // 群组ID，格式为：grp_+UUID
    private String userId;      // 申请人ID，格式为：usr_+UUID
    private String inviterId;   // 邀请人ID，格式为：usr_+UUID
    private String message;     // 验证消息
    private Integer status;     // 状态：0-待处理，1-已同意，2-已拒绝
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
} 