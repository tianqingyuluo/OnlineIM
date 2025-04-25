package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAnnouncement {
    private String groupAnnouncementId;
    private String gid;
    private String userid;
    private String title;
    private String content;
    private Boolean isPinned;
    private Integer status;
    private Date createTime;
    private Date updateTime;
} 