package icu.tianqingyuluo.onlineim.service.call;

import java.util.List;

/**
 * 通话占用与状态。同一用户同时只能有一路通话，多实例必须原子判断。
 */
public interface CallSessionStore {

    enum StartResult {
        OK,
        CALLER_BUSY,
        CALLEE_BUSY
    }

    StartResult tryStart(CallSession session, int ttlSeconds);

    /** 该用户是否还占着一路未结束的通话。 */
    boolean holdsCall(String userId);

    CallSession find(String callId);

    /**
     * 响铃中的通话被某一台设备接听。失败表示已被接听、取消或不存在。
     */
    CallSession markActive(String callId, String calleeConnectionId, long answeredAt, int ttlSeconds);

    /**
     * 仅当当前状态等于 {@code expectedStatus} 时结束并释放双方占用。
     */
    CallSession endIfStatus(String callId, String expectedStatus);

    /** 不论状态，结束仍存在的通话。 */
    CallSession end(String callId);

    /** 领取已经到点的响铃（领取后状态即被移除，多实例只有一个能拿到）。 */
    List<CallSession> claimExpired(long nowMs, int limit);
}
