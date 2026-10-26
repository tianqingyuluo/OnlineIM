package icu.tianqingyuluo.onlineim.service.call;

import java.util.List;

/**
 * 1v1 通话信令帧。客户端只发送 {@link #INBOUND}，服务端再转成同样的帧或终态帧。
 */
public final class CallFrames {

    public static final String INVITE = "CALL_INVITE";
    public static final String OFFER = "CALL_OFFER";
    public static final String RINGING = "CALL_RINGING";
    public static final String ANSWER = "CALL_ANSWER";
    public static final String CANDIDATE = "CALL_CANDIDATE";
    public static final String REJECT = "CALL_REJECT";
    public static final String CANCEL = "CALL_CANCEL";
    public static final String BUSY = "CALL_BUSY";
    public static final String END = "CALL_END";
    public static final String HANGUP = "CALL_HANGUP";
    public static final String HANDLED_ELSEWHERE = "CALL_HANDLED_ELSEWHERE";

    public static final List<String> INBOUND = List.of(
            INVITE, OFFER, RINGING, ANSWER, CANDIDATE, REJECT, CANCEL, END, HANGUP);

    /** 经 Redis Stream 投递、需要在各节点扇出的帧。 */
    public static final List<String> OUTBOUND = List.of(
            INVITE, OFFER, RINGING, ANSWER, CANDIDATE, REJECT, CANCEL, BUSY, END, HANGUP, HANDLED_ELSEWHERE);

    public static final String STATUS_RINGING = "ringing";
    public static final String STATUS_ACTIVE = "active";

    public static final String RECORD_COMPLETED = "completed";
    public static final String RECORD_REJECTED = "rejected";
    public static final String RECORD_CANCELLED = "cancelled";
    public static final String RECORD_MISSED = "missed";
    public static final String RECORD_BUSY = "busy";

    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_AUDIO = "audio";

    private CallFrames() {
    }

    public static boolean isInvite(String type) {
        return INVITE.equals(type) || OFFER.equals(type);
    }

    public static boolean isHangup(String type) {
        return END.equals(type) || HANGUP.equals(type);
    }
}
