package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 创建群组请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupCreateRequest {

    /**
     * 群组名称
     */
    @NotBlank(message = "群组名称不能为空")
    @Size(min = 1, max = 32, message = "群组名称长度必须在1-32之间")
    private String name;
    
    /**
     * 群组头像URL
     */
    private String avatar;
    
    /**
     * 群组描述
     */
    @Size(max = 200, message = "群组描述最多200个字符")
    private String description;
    
    /**
     * 初始成员ID列表
     */
    private List<String> memberIDs;
    
    /**
     * 加入方式：0-需要验证，1-无需验证，2-禁止加入
     */
    private Integer joinType;
    
    /**
     * 最大成员数
     */
    private Integer maxMembers;
} 