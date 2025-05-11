package icu.tianqingyuluo.onlineim.controller;

import cn.hutool.core.util.IdUtil;
import icu.tianqingyuluo.onlineim.pojo.dto.request.AuthRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.UserRegisterRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.AuthResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.User;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
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

    public AuthController(AuthenticationManager authenticationManager,
                          UserDetailsServiceImpl userDetailsService,
                          UserService userService,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    /**
     * 用户登录接口
     * @param authRequest 包含用户名和密码的请求体
     * @return JWT令牌
     */
    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthRequest authRequest) {
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

        // 生成JWT令牌
        final String jwt = jwtUtil.generateToken(userDetails);
        response.put("access_token", jwt);
        response.put("expires_in", JwtUtil.GET_EXPIRE_TIME());
        response.put("user_info", userInfo);

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
                .nickname("anony_" + IdUtil.fastSimpleUUID())
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
        response.put("password", request.getPassword());
        return ResponseEntity.status(201).body(response);
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
        final UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        response.put("access_token", jwtUtil.generateToken(userDetails));
        response.put("expires_in", JwtUtil.GET_EXPIRE_TIME());
        return ResponseEntity.ok(response);
    }

    /**
     * 用户登出
     * @return 登出结果
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization") String token) {
        // UNDO: 把token加入jwt黑名单
        return ResponseEntity.status(204).build();
    }
}