package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友分组实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendGroup {
    private String id;          // 分组ID，格式为：fgrp_+UUID
    private String userId;      // 用户ID，格式为：usr_+UUID
    private String name;        // 分组名称
    private Integer sort;       // 排序权重
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
} 