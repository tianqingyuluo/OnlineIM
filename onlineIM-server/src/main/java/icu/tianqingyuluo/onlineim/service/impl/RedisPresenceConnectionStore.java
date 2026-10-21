package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.service.PresenceConnectionStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Redis 实现。连接键带 TTL，实例崩溃后键过期，其他实例的清理任务会把用户收成离线。
 * Lua 与 {@code InMemoryPresenceConnectionStore} 的返回值约定一致。
 */
@Service
public class RedisPresenceConnectionStore implements PresenceConnectionStore {

    static final String ONLINE_KEY = "presence:online";
    static final String SWEEP_LOCK_KEY = "presence:sweep:lock";
    static final String CONN_PREFIX = "presence:conn:";

    private static final String PRUNE = """
            local function prune(setKey)
              local members = redis.call('SMEMBERS', setKey)
              local alive = 0
              for _, id in ipairs(members) do
                if redis.call('EXISTS', '%s' .. id) == 1 then
                  alive = alive + 1
                else
                  redis.call('SREM', setKey, id)
                end
              end
              return alive
            end
            """.formatted(CONN_PREFIX);

    private static final DefaultRedisScript<Long> CONNECT = script(PRUNE + """
            local before = prune(KEYS[1])
            redis.call('SET', KEYS[2], ARGV[3], 'PX', tonumber(ARGV[2]))
            redis.call('SADD', KEYS[1], ARGV[1])
            redis.call('SADD', KEYS[3], ARGV[3])
            return before
            """);

    private static final DefaultRedisScript<Long> DISCONNECT = script(PRUNE + """
            local existed = redis.call('EXISTS', KEYS[2])
            if existed == 1 then
              redis.call('DEL', KEYS[2])
            end
            redis.call('SREM', KEYS[1], ARGV[1])
            local after = prune(KEYS[1])
            if after > 0 then
              if existed == 1 then return 1 else return 0 end
            end
            local removed = redis.call('SREM', KEYS[3], ARGV[2])
            if removed == 1 then
              redis.call('SET', KEYS[4], ARGV[3])
              return 2
            end
            return 0
            """);

    private static final DefaultRedisScript<Long> HEARTBEAT = script("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
              redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[1]))
              return 1
            end
            return 0
            """);

    private static final DefaultRedisScript<Long> SWEEP = script(PRUNE + """
            local after = prune(KEYS[1])
            if after > 0 then return 0 end
            local removed = redis.call('SREM', KEYS[2], ARGV[1])
            if removed == 1 then
              redis.call('SET', KEYS[3], ARGV[2])
              return 1
            end
            return 0
            """);

    private static final DefaultRedisScript<Long> ALIVE = script(PRUNE + """
            return prune(KEYS[1])
            """);

    private final StringRedisTemplate redis;

    public RedisPresenceConnectionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public int connect(String userId, String connectionId, long nowMs, long ttlMs) {
        Long before = redis.execute(CONNECT,
                List.of(connsKey(userId), connKey(connectionId), ONLINE_KEY),
                connectionId, Long.toString(ttlMs), userId);
        return before == null ? 0 : before.intValue();
    }

    @Override
    public int disconnect(String userId, String connectionId, long nowMs) {
        Long code = redis.execute(DISCONNECT,
                List.of(connsKey(userId), connKey(connectionId), ONLINE_KEY, lastSeenKey(userId)),
                connectionId, userId, Long.toString(nowMs));
        return code == null ? DISCONNECT_UNCHANGED : code.intValue();
    }

    @Override
    public boolean heartbeat(String userId, String connectionId, long nowMs, long ttlMs) {
        Long alive = redis.execute(HEARTBEAT, List.of(connKey(connectionId)), Long.toString(ttlMs));
        return alive != null && alive == 1L;
    }

    @Override
    public int aliveCount(String userId, long nowMs) {
        Long count = redis.execute(ALIVE, List.of(connsKey(userId)));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public Long getLastSeen(String userId) {
        String value = redis.opsForValue().get(lastSeenKey(userId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void setHidden(String userId, boolean hidden) {
        if (hidden) {
            redis.opsForValue().set(hiddenKey(userId), "1");
        } else {
            redis.delete(hiddenKey(userId));
        }
    }

    @Override
    public boolean isHidden(String userId) {
        return "1".equals(redis.opsForValue().get(hiddenKey(userId)));
    }

    @Override
    public Set<String> onlineUserIds() {
        Set<String> members = redis.opsForSet().members(ONLINE_KEY);
        return members == null ? Set.of() : Set.copyOf(members);
    }

    @Override
    public boolean sweepOffline(String userId, long nowMs) {
        Long changed = redis.execute(SWEEP,
                List.of(connsKey(userId), ONLINE_KEY, lastSeenKey(userId)),
                userId, Long.toString(nowMs));
        return changed != null && changed == 1L;
    }

    @Override
    public boolean trySweepLock(String owner, long ttlMs) {
        Boolean locked = redis.opsForValue().setIfAbsent(SWEEP_LOCK_KEY, owner, Duration.ofMillis(ttlMs));
        return Boolean.TRUE.equals(locked);
    }

    static String connsKey(String userId) {
        return "presence:conns:" + userId;
    }

    static String connKey(String connectionId) {
        return CONN_PREFIX + connectionId;
    }

    static String lastSeenKey(String userId) {
        return "presence:last_seen:" + userId;
    }

    static String hiddenKey(String userId) {
        return "presence:hidden:" + userId;
    }

    private static DefaultRedisScript<Long> script(String text) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(text);
        script.setResultType(Long.class);
        return script;
    }
}
