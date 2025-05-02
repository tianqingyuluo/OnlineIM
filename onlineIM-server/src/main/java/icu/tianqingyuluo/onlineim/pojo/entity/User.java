package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String id;           // 用户ID，格式为：usr_+UUID
    private String username;     // 用户名
    private String password;     // 密码哈希值
    private String nickname;     // 昵称
    private String avatar;       // 头像URL
    private Integer gender;      // 性别：0-未知，1-男，2-女
    private String signature;    // 个性签名
    private String region;       // 地区
    private String email;        // 邮箱
    private String phone;        // 手机号
    private Integer status;      // 账号状态：0-禁用，1-正常
    private LocalDateTime lastLoginTime; // 最后登录时间
    private String lastLoginIp;  // 最后登录IP
    private LocalDateTime createdAt;    // 创建时间
    private LocalDateTime updatedAt;    // 更新时间
}