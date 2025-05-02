package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 群组邀请请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInvitationRequest {
    
    /**
     * 要邀请的用户ID列表
     */
    private List<String> userIds;
} 