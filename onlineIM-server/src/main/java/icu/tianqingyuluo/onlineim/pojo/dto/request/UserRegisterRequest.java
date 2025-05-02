package icu.tianqingyuluo.onlineim.pojo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户注册请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {

    /**
     * 用户名，长度4-16个字符
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 16, message = "用户名长度必须在4-16之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;
    
    /**
     * 密码，长度6-20个字符
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20之间")
    private String password;
    
    /**
     * 昵称，长度1-16个字符
     */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 16, message = "昵称长度必须在1-16之间")
    private String nickname;
    
    /**
     * 邮箱，可选
     */
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 手机号，可选
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$|^$", message = "手机号格式不正确")
    private String phone;
} 