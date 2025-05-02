package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.UserLoginRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.UserRegisterRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.UserUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody UserRegisterRequest request) {
        // TODO: 实现注册逻辑
        return null;
    }
    
    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录结果，包含token
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody UserLoginRequest request) {
        // TODO: 实现登录逻辑
        return null;
    }
    
    /**
     * 获取当前用户信息
     * @return 用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        // TODO: 实现获取当前用户信息逻辑
        return null;
    }
    
    /**
     * 获取指定用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserInfo(@PathVariable String userId) {
        // TODO: 实现获取指定用户信息逻辑
        return null;
    }
    
    /**
     * 更新用户信息
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateUserInfo(@RequestBody UserUpdateRequest request) {
        // TODO: 实现更新用户信息逻辑
        return null;
    }
    
    /**
     * 上传头像
     * @param avatar 头像文件
     * @return 更新结果
     */
    @PostMapping("/me/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile avatar) {
        // TODO: 实现上传头像逻辑
        return null;
    }
    
    /**
     * 修改密码
     * @param request 包含旧密码和新密码的请求
     * @return 修改结果
     */
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> updatePassword(@RequestBody Map<String, String> request) {
        // TODO: 实现修改密码逻辑
        return null;
    }
    
    /**
     * 搜索用户
     * @param keyword 关键词
     * @return 用户列表
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserBriefResponse>> searchUsers(@RequestParam String keyword) {
        // TODO: 实现搜索用户逻辑
        return null;
    }
    
    /**
     * 刷新token
     * @return 新token
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, String>> refreshToken() {
        // TODO: 实现刷新token逻辑
        return null;
    }
    
    /**
     * 用户登出
     * @return 登出结果
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // TODO: 实现登出逻辑
        return null;
    }
} 