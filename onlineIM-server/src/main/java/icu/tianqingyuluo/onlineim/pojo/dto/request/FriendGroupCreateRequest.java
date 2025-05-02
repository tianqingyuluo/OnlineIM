package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 好友分组创建请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendGroupCreateRequest {
    
    /**
     * 分组名称
     */
    @NotBlank(message = "分组名称不能为空")
    @Size(min = 1, max = 16, message = "分组名称长度必须在1-16之间")
    private String name;
    
    /**
     * 排序权重
     */
    private Integer sort;
} 