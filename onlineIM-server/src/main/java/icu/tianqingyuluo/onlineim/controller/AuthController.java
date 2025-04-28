package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.AuthRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.AuthResponse;
import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager, UserDetailsServiceImpl userDetailsService, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
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
            return ResponseEntity.status(401).body("用户名或密码错误");
        }

        // 身份验证成功，加载用户详情
        final UserDetails userDetails = userDetailsService.loadUserByUsername(authRequest.getUsername());

        // 生成JWT令牌
        final String jwt = jwtUtil.generateToken(userDetails);

        // 返回JWT令牌
        return ResponseEntity.ok(new AuthResponse(jwt));
    }

//    @PostMapping("/register")
//    public
}