package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群组公告实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAnnouncement {
    private String id;          // 公告ID，格式为：ann_+UUID
    private String groupId;     // 群组ID，格式为：grp_+UUID
    private String publisherId; // 发布者ID，格式为：usr_+UUID
    private String title;       // 公告标题
    private String content;     // 公告内容
    private Integer status;     // 公告状态：0-已删除，1-正常
    private Integer pin;        // 是否置顶：0-不置顶，1-置顶
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
} 