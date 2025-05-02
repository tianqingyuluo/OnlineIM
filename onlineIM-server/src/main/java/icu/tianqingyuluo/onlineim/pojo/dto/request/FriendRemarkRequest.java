package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好友备注请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendRemarkRequest {
    
    /**
     * 备注名
     */
    private String remark;
} 