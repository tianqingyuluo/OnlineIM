package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群组操作请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupActionRequest {
    
    /**
     * 操作类型：accept - 接受, reject - 拒绝
     */
    private String action;
} 