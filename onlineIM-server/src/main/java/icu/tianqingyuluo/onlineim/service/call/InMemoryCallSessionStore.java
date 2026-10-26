package icu.tianqingyuluo.onlineim.service.call;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单测与本地单进程使用的通话表。语义与 {@link RedisCallSessionStore} 一致：
 * 同一用户不能同时占用两路，过期响铃只会被领取一次。
 */
public class InMemoryCallSessionStore implements CallSessionStore {

    private final ConcurrentHashMap<String, CallSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userCalls = new ConcurrentHashMap<>();

    @Override
    public synchronized StartResult tryStart(CallSession session, int ttlSeconds) {
        if (session == null || session.getCallId() == null) {
            throw new IllegalArgumentException("通话缺少 call_id");
        }
        if (userCalls.containsKey(session.getCallerId())) {
            return StartResult.CALLER_BUSY;
        }
        if (userCalls.containsKey(session.getCalleeId())) {
            return StartResult.CALLEE_BUSY;
        }
        sessions.put(session.getCallId(), session.copy());
        userCalls.put(session.getCallerId(), session.getCallId());
        userCalls.put(session.getCalleeId(), session.getCallId());
        return StartResult.OK;
    }

    @Override
    public synchronized boolean holdsCall(String userId) {
        return userId != null && userCalls.containsKey(userId);
    }

    @Override
    public synchronized CallSession find(String callId) {
        CallSession session = sessions.get(callId);
        return session == null ? null : session.copy();
    }

    @Override
    public synchronized CallSession markActive(String callId, String calleeConnectionId, long answeredAt, int ttlSeconds) {
        CallSession session = sessions.get(callId);
        if (session == null || !CallFrames.STATUS_RINGING.equals(session.getStatus())) {
            return null;
        }
        session.setStatus(CallFrames.STATUS_ACTIVE);
        session.setCalleeConnectionId(calleeConnectionId);
        session.setAnsweredAt(answeredAt);
        return session.copy();
    }

    @Override
    public synchronized CallSession endIfStatus(String callId, String expectedStatus) {
        CallSession session = sessions.get(callId);
        if (session == null || expectedStatus == null || !expectedStatus.equals(session.getStatus())) {
            return null;
        }
        return remove(session);
    }

    @Override
    public synchronized CallSession end(String callId) {
        CallSession session = sessions.get(callId);
        return session == null ? null : remove(session);
    }

    @Override
    public synchronized List<CallSession> claimExpired(long nowMs, int limit) {
        List<CallSession> expired = new ArrayList<>();
        int cap = limit <= 0 ? 20 : limit;
        for (CallSession session : sessions.values()) {
            if (expired.size() >= cap) {
                break;
            }
            if (CallFrames.STATUS_RINGING.equals(session.getStatus()) && session.getRingDeadline() <= nowMs) {
                expired.add(remove(session));
            }
        }
        return expired;
    }

    private CallSession remove(CallSession session) {
        sessions.remove(session.getCallId(), session);
        release(session.getCallerId(), session.getCallId());
        release(session.getCalleeId(), session.getCallId());
        return session.copy();
    }

    private void release(String userId, String callId) {
        if (userId != null) {
            userCalls.remove(userId, callId);
        }
    }
}
