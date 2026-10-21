package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证请求DTO
 * 用于接收用户登录请求中的用户名和密码
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    /**
     * 登录只拒绝空值。已有账号可能是在校验生效前注册的，不能再用注册时的长度规则把它们挡在门外。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名过长")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码过长")
    private String password;

    @JsonProperty("device_id")
    private String deviceId;
}