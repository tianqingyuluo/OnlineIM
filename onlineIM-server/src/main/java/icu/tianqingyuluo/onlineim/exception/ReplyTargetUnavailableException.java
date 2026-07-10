package icu.tianqingyuluo.onlineim.exception;

public class ReplyTargetUnavailableException extends RuntimeException {
    public static final String CODE = "REPLY_TARGET_UNAVAILABLE";

    public ReplyTargetUnavailableException() {
        super("原消息已不可用，请取消引用后重新发送");
    }

    public String getCode() {
        return CODE;
    }
}
