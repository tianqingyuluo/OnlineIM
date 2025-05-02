package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 群公告请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAnnouncementRequest {
    
    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
    @Size(min = 1, max = 50, message = "公告标题长度必须在1-50之间")
    private String title;
    
    /**
     * 公告内容
     */
    @NotBlank(message = "公告内容不能为空")
    @Size(max = 2000, message = "公告内容最多2000个字符")
    private String content;
    
    /**
     * 是否置顶
     */
    private Boolean pin;
} 