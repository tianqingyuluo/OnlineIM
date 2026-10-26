package icu.tianqingyuluo.onlineim.service.call;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 跨实例共享的 1v1 通话。媒体不经过服务端，这里只保存信令状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession {

    private String callId;
    private String callerId;
    private String calleeId;
    private String conversationId;
    private String callType;
    private String status;
    private String callerConnectionId;
    private String calleeConnectionId;
    private long createdAt;
    private long ringDeadline;
    private long answeredAt;

    public String peerOf(String userId) {
        if (userId != null && userId.equals(callerId)) {
            return calleeId;
        }
        if (userId != null && userId.equals(calleeId)) {
            return callerId;
        }
        return null;
    }

    public boolean participant(String userId) {
        return peerOf(userId) != null;
    }

    public CallSession copy() {
        return CallSession.builder()
                .callId(callId)
                .callerId(callerId)
                .calleeId(calleeId)
                .conversationId(conversationId)
                .callType(callType)
                .status(status)
                .callerConnectionId(callerConnectionId)
                .calleeConnectionId(calleeConnectionId)
                .createdAt(createdAt)
                .ringDeadline(ringDeadline)
                .answeredAt(answeredAt)
                .build();
    }
}
