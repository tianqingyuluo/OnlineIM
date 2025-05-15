package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群组加入请求响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupJoinRequestResponse {
    
    /**
     * 请求ID
     */
    @JsonProperty("request_id")
    private String requestID;
    
    /**
     * 群组ID
     */
    @JsonProperty("group_id")
    private String groupID;
    
    /**
     * 群组名称
     */
    @JsonProperty("group_name")
    private String groupName;
    
    /**
     * 申请者信息
     */
    @JsonProperty("user_info")
    private UserBriefResponse userInfo;
    
    /**
     * 邀请者信息（如果是邀请）
     */
    @JsonProperty("inviter_info")
    private UserBriefResponse inviterInfo;
    
    /**
     * 验证消息
     */
    private String message;
    
    /**
     * 状态：pending, accepted, rejected
     */
    private String status;
    
    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    private String createdAt;
} 