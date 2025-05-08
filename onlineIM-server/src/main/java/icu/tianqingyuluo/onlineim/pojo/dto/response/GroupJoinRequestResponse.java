package icu.tianqingyuluo.onlineim.pojo.dto.response;

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
    private String requestID;
    
    /**
     * 群组ID
     */
    private String groupID;
    
    /**
     * 群组名称
     */
    private String groupName;
    
    /**
     * 申请者信息
     */
    private UserBriefResponse userInfo;
    
    /**
     * 邀请者信息（如果是邀请）
     */
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
    private String createdAt;
} 