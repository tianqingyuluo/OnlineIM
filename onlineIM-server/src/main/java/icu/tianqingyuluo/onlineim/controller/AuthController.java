package icu.tianqingyuluo.onlineim.controller;

import cn.hutool.core.util.IdUtil;
import icu.tianqingyuluo.onlineim.pojo.dto.request.AuthRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.UserRegisterRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.AuthResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import icu.tianqingyuluo.onlineim.service.DeviceSessionService;
import icu.tianqingyuluo.onlineim.service.JwtService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.util.DeviceTypes;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 处理用户登录和令牌生成
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final UserDetailsServiceImpl userDetailsService;

    private final UserService userService;

    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder bCryptPasswordEncoder =  new BCryptPasswordEncoder();
    private final JwtService jwtService;
    private final DeviceSessionService deviceSessionService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserDetailsServiceImpl userDetailsService,
                          UserService userService,
                          JwtUtil jwtUtil, JwtService jwtService,
                          DeviceSessionService deviceSessionService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.jwtService = jwtService;
        this.deviceSessionService = deviceSessionService;
    }

    /**
     * 用户登录接口
     * @param authRequest 包含用户名和密码的请求体
     * @return JWT令牌
     */
    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@Valid @RequestBody AuthRequest authRequest) {
        try {
            // 尝试进行身份验证
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword())
            );
        } catch (BadCredentialsException e) {
            // 如果身份验证失败，返回401未授权状态
            Map<String, Object> response = new HashMap<>();
            response.put("code", "401");
            response.put("message", "用户名或密码错误");
            return ResponseEntity.status(401).body(response);
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        Map<String, String> userInfo = new HashMap<>();
        // 身份验证成功，加载用户详情
        final UserDetails userDetails = userDetailsService.loadUserByUsername(authRequest.getUsername());

        UserBriefResponse briefResponse = userService.getUserBriefInfoByUsername(userDetails.getUsername());
        userInfo.put("user_id", briefResponse.getUserId());
        userInfo.put("username", userDetails.getUsername());
        userInfo.put("nickname", briefResponse.getNickname());
        userInfo.put("avatar_url", briefResponse.getAvatarUrl());

        // 生成JWT令牌，并按设备类型挤掉同类型的旧会话
        String deviceType = DeviceTypes.normalize(authRequest.getDeviceType());
        String deviceId = DeviceTypes.sanitizeDeviceId(authRequest.getDeviceId());
        if (deviceId == null) {
            deviceId = deviceType + "-" + IdUtil.fastSimpleUUID();
        }
        final String jwt = jwtUtil.generateToken(userDetails, deviceId, deviceType);
        deviceSessionService.onLogin(briefResponse.getUserId(), deviceId, deviceType, "Bearer " + jwt);
        response.put("access_token", jwt);
        response.put("expires_in", JwtUtil.GET_EXPIRE_TIME());
        response.put("user_info", userInfo);
        response.put("device_id", deviceId);
        response.put("device_type", deviceType);

        // 返回JWT令牌
        return ResponseEntity.ok(response);
    }

    /**
     * 用户注册
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody UserRegisterRequest request) {
        // TODO: 实现注册逻辑
        Map<String, Object> response = new HashMap<>();

        // 用户名重复检验
        if (userService.existsByUsername(request.getUsername())) {
            response.put("error", "用户名已存在");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        // 初始化用户账号元数据
        User user = User
                .builder()
                .id("usr_" + IdUtil.getSnowflakeNextIdStr())
                .username(request.getUsername())
                .nickname(resolveNickname(request.getNickname()))
                .password(bCryptPasswordEncoder.encode(request.getPassword()))
                .build();

        try {
            userService.registerUser(user);
        }
        catch (PersistenceException e) {
            response.put("error", "数据库事务失败");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }


        response.put("user_id", user.getId());
        response.put("username", request.getUsername());
        response.put("nickname", user.getNickname());
        return ResponseEntity.status(201).body(response);
    }

    /**
     * 注册时使用用户填写的昵称（去掉首尾空白）；未填写或只有空白时生成 anony_ 匿名昵称。
     */
    static String resolveNickname(String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            return nickname.strip();
        }
        return "anony_" + IdUtil.fastSimpleUUID();
    }

    /**
     * 刷新token
     * @return 新token
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, String>> refreshToken(@RequestHeader(value = "Authorization") String token) {
        // UNDO: 后续需要加入把作废的token加入到redis的操作，然后在jwtutil中加入检查redis内token黑名单的逻辑
        Map<String, String> response = new HashMap<>();
        String username = jwtUtil.getUsernameFromToken(token);
        String deviceId = jwtUtil.getDeviceIDFromToken(token);
        String deviceType = jwtUtil.getDeviceTypeFromToken(token);
        String userId = jwtUtil.getUserIDFromToken(token);
        jwtService.invalidateJWT(token);
        final UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String accessToken = jwtUtil.generateToken(userDetails, deviceId, deviceType);
        deviceSessionService.replaceToken(userId, deviceId, "Bearer " + accessToken);
        response.put("access_token", accessToken);
        response.put("expires_in", JwtUtil.GET_EXPIRE_TIME());
        return ResponseEntity.ok(response);
    }

    /**
     * 用户登出
     * @return 登出结果
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization") String token) {
        try {
            deviceSessionService.removeLogin(jwtUtil.getUserIDFromToken(token), jwtUtil.getDeviceIDFromToken(token));
        } catch (RuntimeException e) {
            log.warn("登出时清理设备会话失败: {}", e.getMessage());
        }
        jwtService.invalidateJWT(token);
        return ResponseEntity.status(204).build();
    }
}