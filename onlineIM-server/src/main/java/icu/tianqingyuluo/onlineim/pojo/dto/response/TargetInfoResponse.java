package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话目标信息响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetInfoResponse {
    
    /**
     * 目标ID（用户ID或群组ID）
     */
    private String id;
    
    /**
     * 目标名称（用户昵称或群组名称）
     */
    private String name;
    
    /**
     * 头像URL
     */
    @JsonProperty("avatar_url")
    private String avatarUrl;
} 