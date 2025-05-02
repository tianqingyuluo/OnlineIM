package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友请求实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequest {
    private String id;           // 请求ID，格式为：req_+UUID
    private String fromUserId;   // 发送者ID，格式为：usr_+UUID
    private String toUserId;     // 接收者ID，格式为：usr_+UUID
    private String message;      // 验证消息
    private Integer status;      // 状态：0-待处理，1-已同意，2-已拒绝
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
} 