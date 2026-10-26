package icu.tianqingyuluo.onlineim.service.call;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 用 Redis Lua 保证多实例下的占用、接听和响铃超时只生效一次。
 */
@Component
public class RedisCallSessionStore implements CallSessionStore {

    static final String RINGING_KEY = "im:call:ringing";
    private static final String SESSION_PREFIX = "im:call:session:";
    private static final String USER_PREFIX = "im:call:user:";
    private static final char SEP = '\u001f';

    private static final DefaultRedisScript<String> START = script("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 'CALLER_BUSY' end
            if redis.call('EXISTS', KEYS[2]) == 1 then return 'CALLEE_BUSY' end
            redis.call('HSET', KEYS[3],
              'call_id', ARGV[1], 'caller_id', ARGV[2], 'callee_id', ARGV[3],
              'conversation_id', ARGV[4], 'call_type', ARGV[5], 'status', ARGV[6],
              'caller_connection_id', ARGV[7], 'callee_connection_id', '',
              'created_at', ARGV[8], 'ring_deadline', ARGV[9], 'answered_at', '0')
            redis.call('EXPIRE', KEYS[3], ARGV[10])
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[10])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[10])
            redis.call('ZADD', KEYS[4], ARGV[9], ARGV[1])
            return 'OK'
            """);

    private static final DefaultRedisScript<String> ACTIVATE = script("""
            local status = redis.call('HGET', KEYS[1], 'status')
            if status ~= 'ringing' then return '' end
            redis.call('HSET', KEYS[1], 'status', 'active', 'callee_connection_id', ARGV[1], 'answered_at', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            redis.call('EXPIRE', KEYS[3], ARGV[3])
            redis.call('ZREM', KEYS[4], ARGV[4])
            return 'OK'
            """);

    private static final DefaultRedisScript<String> END_IF = script("""
            local status = redis.call('HGET', KEYS[1], 'status')
            if not status or status ~= ARGV[1] then return '' end
            local packed = redis.call('HGET', KEYS[1], 'call_id') .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'caller_id') or '') .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'callee_id') or '') .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'conversation_id') or '') .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'call_type') or '') .. '\u001f'
              .. status .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'caller_connection_id') or '') .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'callee_connection_id') or '') .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'created_at') or '0') .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'ring_deadline') or '0') .. '\u001f'
              .. (redis.call('HGET', KEYS[1], 'answered_at') or '0')
            local callId = redis.call('HGET', KEYS[1], 'call_id')
            local caller = redis.call('HGET', KEYS[1], 'caller_id')
            local callee = redis.call('HGET', KEYS[1], 'callee_id')
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], callId)
            if caller and redis.call('GET', 'im:call:user:' .. caller) == callId then
              redis.call('DEL', 'im:call:user:' .. caller)
            end
            if callee and redis.call('GET', 'im:call:user:' .. callee) == callId then
              redis.call('DEL', 'im:call:user:' .. callee)
            end
            return packed
            """);

    private static final DefaultRedisScript<String> CLAIM = script("""
            local ids = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, tonumber(ARGV[2]))
            local lines = {}
            for _, callId in ipairs(ids) do
              redis.call('ZREM', KEYS[1], callId)
              local key = 'im:call:session:' .. callId
              local status = redis.call('HGET', key, 'status')
              if status == 'ringing' then
                local packed = (redis.call('HGET', key, 'call_id') or '') .. '\u001f'
                  .. (redis.call('HGET', key, 'caller_id') or '') .. '\u001f'
                  .. (redis.call('HGET', key, 'callee_id') or '') .. '\u001f'
                  .. (redis.call('HGET', key, 'conversation_id') or '') .. '\u001f'
                  .. (redis.call('HGET', key, 'call_type') or '') .. '\u001f'
                  .. status .. '\u001f'
                  .. (redis.call('HGET', key, 'caller_connection_id') or '') .. '\u001f'
                  .. (redis.call('HGET', key, 'callee_connection_id') or '') .. '\u001f'
                  .. (redis.call('HGET', key, 'created_at') or '0') .. '\u001f'
                  .. (redis.call('HGET', key, 'ring_deadline') or '0') .. '\u001f'
                  .. (redis.call('HGET', key, 'answered_at') or '0')
                local caller = redis.call('HGET', key, 'caller_id')
                local callee = redis.call('HGET', key, 'callee_id')
                redis.call('DEL', key)
                if caller and redis.call('GET', 'im:call:user:' .. caller) == callId then
                  redis.call('DEL', 'im:call:user:' .. caller)
                end
                if callee and redis.call('GET', 'im:call:user:' .. callee) == callId then
                  redis.call('DEL', 'im:call:user:' .. callee)
                end
                table.insert(lines, packed)
              end
            end
            return table.concat(lines, '\\n')
            """);

    private final StringRedisTemplate redis;

    public RedisCallSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public StartResult tryStart(CallSession session, int ttlSeconds) {
        String result = redis.execute(
                START,
                List.of(userKey(session.getCallerId()), userKey(session.getCalleeId()), sessionKey(session.getCallId()), RINGING_KEY),
                session.getCallId(),
                nullToEmpty(session.getCallerId()),
                nullToEmpty(session.getCalleeId()),
                nullToEmpty(session.getConversationId()),
                nullToEmpty(session.getCallType()),
                nullToEmpty(session.getStatus()),
                nullToEmpty(session.getCallerConnectionId()),
                Long.toString(session.getCreatedAt()),
                Long.toString(session.getRingDeadline()),
                Integer.toString(Math.max(ttlSeconds, 1)));
        if ("CALLER_BUSY".equals(result)) {
            return StartResult.CALLER_BUSY;
        }
        if ("CALLEE_BUSY".equals(result)) {
            return StartResult.CALLEE_BUSY;
        }
        if (!"OK".equals(result)) {
            throw new IllegalStateException("创建通话失败");
        }
        return StartResult.OK;
    }

    @Override
    public boolean holdsCall(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(userKey(userId)));
    }

    @Override
    public CallSession find(String callId) {
        List<Object> values = redis.opsForHash().multiGet(sessionKey(callId), new ArrayList<>(fields()));
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return null;
        }
        return fromFields(values);
    }

    @Override
    public CallSession markActive(String callId, String calleeConnectionId, long answeredAt, int ttlSeconds) {
        CallSession existing = find(callId);
        if (existing == null) {
            return null;
        }
        String result = redis.execute(
                ACTIVATE,
                List.of(sessionKey(callId), userKey(existing.getCallerId()), userKey(existing.getCalleeId()), RINGING_KEY),
                nullToEmpty(calleeConnectionId),
                Long.toString(answeredAt),
                Integer.toString(Math.max(ttlSeconds, 1)),
                callId);
        if (!"OK".equals(result)) {
            return null;
        }
        existing.setStatus(CallFrames.STATUS_ACTIVE);
        existing.setCalleeConnectionId(calleeConnectionId);
        existing.setAnsweredAt(answeredAt);
        return existing;
    }

    @Override
    public CallSession endIfStatus(String callId, String expectedStatus) {
        String packed = redis.execute(END_IF, List.of(sessionKey(callId), RINGING_KEY), expectedStatus);
        return unpack(packed);
    }

    @Override
    public CallSession end(String callId) {
        CallSession session = endIfStatus(callId, CallFrames.STATUS_RINGING);
        return session != null ? session : endIfStatus(callId, CallFrames.STATUS_ACTIVE);
    }

    @Override
    public List<CallSession> claimExpired(long nowMs, int limit) {
        String packed = redis.execute(
                CLAIM,
                List.of(RINGING_KEY),
                Long.toString(nowMs),
                Integer.toString(limit <= 0 ? 20 : limit));
        if (packed == null || packed.isBlank()) {
            return List.of();
        }
        List<CallSession> sessions = new ArrayList<>();
        for (String line : packed.split("\n")) {
            CallSession session = unpack(line);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    private static CallSession unpack(String packed) {
        if (packed == null || packed.isBlank()) {
            return null;
        }
        String[] parts = packed.split(String.valueOf(SEP), -1);
        if (parts.length < 11 || parts[0].isBlank()) {
            return null;
        }
        return fromFields(Arrays.asList(parts));
    }

    private static CallSession fromFields(List<?> values) {
        return CallSession.builder()
                .callId(text(values, 0))
                .callerId(text(values, 1))
                .calleeId(text(values, 2))
                .conversationId(text(values, 3))
                .callType(text(values, 4))
                .status(text(values, 5))
                .callerConnectionId(text(values, 6))
                .calleeConnectionId(text(values, 7))
                .createdAt(longValue(values, 8))
                .ringDeadline(longValue(values, 9))
                .answeredAt(longValue(values, 10))
                .build();
    }

    private static List<String> fields() {
        return List.of(
                "call_id", "caller_id", "callee_id", "conversation_id", "call_type", "status",
                "caller_connection_id", "callee_connection_id", "created_at", "ring_deadline", "answered_at");
    }

    private static String text(List<?> values, int index) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return "";
        }
        return String.valueOf(values.get(index));
    }

    private static long longValue(List<?> values, int index) {
        try {
            return Long.parseLong(text(values, index));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String sessionKey(String callId) {
        return SESSION_PREFIX + callId;
    }

    private static String userKey(String userId) {
        return USER_PREFIX + userId;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static DefaultRedisScript<String> script(String text) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(text);
        script.setResultType(String.class);
        return script;
    }

}
