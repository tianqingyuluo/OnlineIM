package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 好友分组响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendGroupResponse {
    
    /**
     * 分组ID
     */
    @JsonProperty("group_id")
    private String groupID;
    
    /**
     * 分组名称
     */
    private String name;
    
    /**
     * 排序权重
     */
    private Integer sort;
    
    /**
     * 分组内好友列表
     */
    private List<FriendResponse> friends;
    
    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    private String createdAt;
} 