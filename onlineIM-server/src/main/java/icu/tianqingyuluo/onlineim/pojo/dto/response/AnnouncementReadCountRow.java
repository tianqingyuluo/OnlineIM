package icu.tianqingyuluo.onlineim.pojo.dto.response;

import lombok.Data;

/** MyBatis 行：某条公告在当前成员中的已读人数 */
@Data
public class AnnouncementReadCountRow {
    private String announcementId;
    private Integer readCount;
}
