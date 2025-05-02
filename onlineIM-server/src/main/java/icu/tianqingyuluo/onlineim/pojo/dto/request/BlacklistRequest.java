package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 黑名单操作请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistRequest {
    
    /**
     * 目标用户ID
     */
    private String targetUserId;
} 