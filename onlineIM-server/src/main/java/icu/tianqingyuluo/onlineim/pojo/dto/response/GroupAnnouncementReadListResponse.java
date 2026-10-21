package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 群主/管理员查看的公告已读名单。人数只统计当前仍在群里的成员。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAnnouncementReadListResponse {

    @JsonProperty("announcement_id")
    private String announcementId;

    @JsonProperty("group_id")
    private String groupId;

    @JsonProperty("read_count")
    private Integer readCount;

    @JsonProperty("unread_count")
    private Integer unreadCount;

    private List<AnnouncementReaderResponse> readers;

    @JsonProperty("unread_users")
    private List<AnnouncementReaderResponse> unreadUsers;
}
