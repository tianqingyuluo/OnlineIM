package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.UserUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserResponse;
import icu.tianqingyuluo.onlineim.service.FileStorageService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.storage.OSSAdapter;
import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    private final JwtUtil jwtUtil;

    private final FileStorageService fileStorageService;

    @Value("${oss.default-bucket}")
    private String bucketName;

    public UserController(UserDetailsServiceImpl userDetailsService,
                          UserService userService, JwtUtil jwtUtil,
                          FileStorageService fileStorageService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 获取当前用户信息
     * @return 用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader(value = "Authorization") String token) {
        // TODO: 实现获取当前用户信息逻辑
        String username = jwtUtil.getUsernameFromToken(token);
        return ResponseEntity.ok(userService.getUserInfoByUsername(username));
    }
    
    /**
     * 获取指定用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserInfo(@PathVariable String userId) {
        // TODO: 实现获取指定用户信息逻辑
        try {
            return ResponseEntity.ok(userService.getUserInfoByUserID(userId));
        }
        catch (PersistenceException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发生了些小错误"));
        }
    }
    
    /**
     * 更新用户信息
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateUserInfo(@RequestHeader(value = "Authorization") String token, @RequestBody UserUpdateRequest request) {
        // TODO: 实现更新用户信息逻辑
        String username = jwtUtil.getUsernameFromToken(token);
        try {
            userService.updateByUsername(username, request);
            return ResponseEntity.ok(userService.getUserInfoByUsername(username));
        }
        catch (PersistenceException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发生了些小错误"));
        }
    }
    
    /**
     * 上传头像
     * @param avatar 头像文件
     * @return 更新结果
     */
    @PostMapping("/me/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile avatar) {
        // TODO: 实现上传头像逻辑
        // UNDO: 使用minio传入数据桶中
        Map<String, String> response = new HashMap<>();
        String userID = jwtUtil.getUserIDFromToken(token);

        try {
            String avatarUrl = fileStorageService.uploadAvatar(avatar, userID);
            response.put("avatar_url", avatarUrl);
            return ResponseEntity.ok(response);
        }
        catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "传输文件失败"));
        }
    }
    
    /**
     * 修改密码
     * @param request 包含旧密码和新密码的请求
     * @return 修改结果
     */
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> updatePassword(@RequestHeader(value = "Authorization") String token, @RequestBody Map<String, String> request) {
        // TODO: 实现修改密码逻辑
        Map<String, String> response = new HashMap<>();
        String username = jwtUtil.getUsernameFromToken(token);
        String newPassword = request.get("new_password");
        String oldPassword = request.get("old_password");

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        if (passwordEncoder.matches(oldPassword, userService.getPasswordByUsername(username))) {
            // UNDO: 作废当前token
            userService.updatePasswordByUsername(username, passwordEncoder.encode(newPassword));
            return ResponseEntity.ok().build();
        }
        else return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发生了些小错误"));
    }
    
    /**
     * 搜索用户
     * @param keyword 关键词
     * @return 用户列表
     */
    @GetMapping("/search/{keyword}/{offset}")
    public ResponseEntity<?> searchUsers(@PathVariable("keyword") String keyword, @PathVariable("offset")Integer offset) {
        // TODO: 实现搜索用户逻辑
        final int LIMIT = 5; // 用户搜索页分页参数
        // 先搜索 userid
        List<UserBriefResponse> userBriefResponseList = new ArrayList<>(userService.searchByUserID(keyword, LIMIT, offset));
        // 再搜索 username
        userBriefResponseList.addAll(userService.searchByUsername(keyword, LIMIT, offset));
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> userlist = new ArrayList<>();

        userBriefResponseList.forEach(userBriefResponse -> {
            Map<String, Object> user = new HashMap<>();
            user.put("user_id", userBriefResponse.getUserId());
            user.put("username", userBriefResponse.getUsername());
            user.put("nickname", userBriefResponse.getNickname());
            user.put("avatar_url", userBriefResponse.getAvatarUrl());
            userlist.add(user);
        });

        response.put("users", userlist);
        response.put("total", userlist.size());

        return ResponseEntity.ok(response);
    }

}