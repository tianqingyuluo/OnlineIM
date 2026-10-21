package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群公告响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAnnouncementResponse {
    
    /**
     * 公告ID
     */
    @JsonProperty("announcement_id")
    private String announcementID;
    
    /**
     * 群组ID
     */
    @JsonProperty("group_id")
    private String groupID;
    
    /**
     * 发布者信息
     */
    @JsonProperty("publisher_info")
    private UserBriefResponse publisherInfo;
    
    /**
     * 公告标题
     */
    private String title;
    
    /**
     * 公告内容
     */
    private String content;
    
    /**
     * 是否置顶
     */
    private Boolean pinned;
    
    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    private String createdAt;
    
    /**
     * 更新时间
     */
    @JsonProperty("updated_at")
    private String updatedAt;

    /** 当前用户是否已确认阅读 */
    @JsonProperty("read_by_me")
    private Boolean readByMe;

    /** 已读人数（仅群主/管理员返回） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("read_count")
    private Integer readCount;

    /** 未读人数（仅群主/管理员返回，只计当前在群成员） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("unread_count")
    private Integer unreadCount;
} 