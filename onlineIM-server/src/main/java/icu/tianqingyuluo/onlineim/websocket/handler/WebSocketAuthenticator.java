package icu.tianqingyuluo.onlineim.websocket.handler;

import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * WebSocket认证器
 * 负责验证WebSocket连接请求中的JWT令牌
 * 与框架解耦，不依赖特定的WebSocket实现
 */
@Slf4j
@Component
public class WebSocketAuthenticator {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public WebSocketAuthenticator(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    /**
     * 验证JWT令牌
     * @param token JWT令牌（可带或不带Bearer前缀）
     * @return 认证结果，包含用户信息或失败原因
     */
    public AuthResult authenticate(String token) {
        if (token == null || token.isBlank()) {
            log.warn("WebSocket认证失败: 令牌为空");
            return AuthResult.failure("令牌为空");
        }

        // 处理Bearer前缀
        String actualToken = token;
        if (token.startsWith("Bearer ")) {
            actualToken = token;
        } else {
            // 如果没有Bearer前缀，添加它以保持兼容性
            actualToken = "Bearer " + token;
        }

        try {
            // 从令牌中获取用户名
            String username = jwtUtil.getUsernameFromToken(actualToken);
            String userId = jwtUtil.getUserIDFromToken(actualToken);
            String deviceId = jwtUtil.getDeviceIDFromToken(actualToken);

            // 加载用户详情
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // 验证令牌
            if (jwtUtil.validateToken(actualToken, userDetails)) {
                long remainingTime = jwtUtil.getRemainingValidityTime(actualToken);
                
                log.info("WebSocket认证成功: username={}, userId={}", username, userId);
                return AuthResult.success(username, userId, deviceId, actualToken, remainingTime);
            } else {
                log.warn("WebSocket认证失败: JWT令牌无效");
                return AuthResult.failure("JWT令牌无效");
            }
        } catch (Exception e) {
            log.error("WebSocket认证异常: {}", e.getMessage());
            return AuthResult.failure("认证异常: " + e.getMessage());
        }
    }

    /**
     * 从URL参数中提取token
     * @param uri 请求URI，例如 /ws?token=Bearer%20xxx
     * @return 提取的token，如果不存在则返回null
     */
    public String extractTokenFromUri(String uri) {
        if (uri == null || !uri.contains("?")) {
            return null;
        }

        String query = uri.substring(uri.indexOf('?') + 1);
        String[] params = query.split("&");
        
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                // URL解码
                try {
                    return java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                } catch (Exception e) {
                    return keyValue[1];
                }
            }
        }
        return null;
    }

    /**
     * 认证结果类
     */
    @Getter
    @AllArgsConstructor
    public static class AuthResult {
        /**
         * 是否认证成功
         */
        private final boolean success;
        
        /**
         * 用户名
         */
        private final String username;
        
        /**
         * 用户ID
         */
        private final String userId;
        
        /**
         * 设备ID
         */
        private final String deviceId;
        
        /**
         * JWT令牌
         */
        private final String token;
        
        /**
         * 令牌剩余有效时间（毫秒）
         */
        private final long remainingTimeMillis;
        
        /**
         * 失败原因（仅在失败时有值）
         */
        private final String failureReason;

        /**
         * 创建成功结果
         */
        public static AuthResult success(String username, String userId, String deviceId, 
                                         String token, long remainingTimeMillis) {
            return new AuthResult(true, username, userId, deviceId, token, remainingTimeMillis, null);
        }

        /**
         * 创建失败结果
         */
        public static AuthResult failure(String reason) {
            return new AuthResult(false, null, null, null, null, 0, reason);
        }
    }
}
