package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final RedisTemplate<String, String> redisTemplate;

    private final JwtUtil jwtUtil;

    private static final String JWT_PREFIX = "user:jwt:";
    private static final String JWT_BLACKLIST_PREFIX = "user:jwt:blacklist:";
    private static final String JWT_REVOKED_BEFORE_PREFIX = "user:jwt:revoked-before:";

    public JwtService(RedisTemplate<String, String> redisTemplate, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    public void invalidateJWT(String token) {
        redisTemplate.opsForZSet().add(JWT_BLACKLIST_PREFIX, token, jwtUtil.getRemainingValidityTime(token));
    }

    /**
     * 作废某个用户此刻之前签发的全部 token（修改密码等场景）。
     * 记录作废时间点，保留到 token 最长有效期结束；之后签发的新 token 不受影响。
     */
    public void revokeAllTokensOfUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(JWT_REVOKED_BEFORE_PREFIX + userId,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofMillis(JwtUtil.getTokenValidityMillis()));
    }

    /**
     * token 是否因用户级作废（如修改密码）而失效
     */
    public boolean isRevokedForUser(String token) {
        try {
            String userId = jwtUtil.getUserIDFromToken(token);
            if (userId == null) {
                return false;
            }
            String revokedBefore = redisTemplate.opsForValue().get(JWT_REVOKED_BEFORE_PREFIX + userId);
            if (revokedBefore == null) {
                return false;
            }
            return jwtUtil.getIssuedAtMillis(token) < Long.parseLong(revokedBefore);
        } catch (RuntimeException e) {
            // 无法解析的 token 交给后续的签名/过期校验处理
            return false;
        }
    }

    public boolean isBlockedToken(String token) {
        if (isRevokedForUser(token)) {
            return true;
        }
        // 1. 检查token是否存在于ZSET中
        Double score = redisTemplate.opsForZSet().score(JWT_BLACKLIST_PREFIX, token);

        // 2. 如果不存在或已过期(score <= 当前时间)，返回false
        if (score == null || score <= System.currentTimeMillis()) {
            // 可选：清理已过期的token
            redisTemplate.opsForZSet().remove(JWT_BLACKLIST_PREFIX, token);
            return false;
        }

        // 3. 如果存在且未过期，返回true
        return true;
    }

}
