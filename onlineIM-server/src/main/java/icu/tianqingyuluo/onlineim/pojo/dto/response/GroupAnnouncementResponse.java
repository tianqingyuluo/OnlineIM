package icu.tianqingyuluo.onlineim.pojo.dto.response;

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
} 