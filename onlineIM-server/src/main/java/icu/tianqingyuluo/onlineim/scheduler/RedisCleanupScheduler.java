package icu.tianqingyuluo.onlineim.scheduler;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RedisCleanupScheduler {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String JWT_BLACKLIST_PREFIX = "user:jwt:blacklist:";

    public RedisCleanupScheduler(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 每一小时清理一次过期的黑名单里的JWT token
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void cleanupInvalidJWTInBlacklist() {
        redisTemplate.opsForZSet().removeRangeByScore(JWT_BLACKLIST_PREFIX, 0, System.currentTimeMillis());
    }
}
