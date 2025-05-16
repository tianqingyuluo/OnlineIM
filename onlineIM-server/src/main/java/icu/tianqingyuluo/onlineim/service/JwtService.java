package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

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
        redisTemplate.opsForSet().add(JWT_BLACKLIST_PREFIX, token);
        redisTemplate.expire(JWT_BLACKLIST_PREFIX, jwtUtil.getRemainingValidityTime(token), TimeUnit.MILLISECONDS);
    }

    public boolean isBlockedToken(String token) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(JWT_PREFIX + "blacklist:", token));
    }

}
