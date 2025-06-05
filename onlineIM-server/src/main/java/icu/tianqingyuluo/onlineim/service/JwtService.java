package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final RedisTemplate<String, String> redisTemplate;

    private final JwtUtil jwtUtil;

    private static final String JWT_PREFIX = "user:jwt:";
    private static final String JWT_BLACKLIST_PREFIX = "user:jwt:blacklist:";

    public JwtService(RedisTemplate<String, String> redisTemplate, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    public void invalidateJWT(String token) {
        redisTemplate.opsForZSet().add(JWT_BLACKLIST_PREFIX, token, jwtUtil.getRemainingValidityTime(token));
    }

    public boolean isBlockedToken(String token) {
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
