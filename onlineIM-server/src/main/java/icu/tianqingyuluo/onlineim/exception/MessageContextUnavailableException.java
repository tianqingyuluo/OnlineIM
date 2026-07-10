package icu.tianqingyuluo.onlineim.exception;

public class MessageContextUnavailableException extends RuntimeException {
    public static final String CODE = "MESSAGE_CONTEXT_UNAVAILABLE";

    public MessageContextUnavailableException() {
        super("原消息不可用");
    }

    public String getCode() {
        return CODE;
    }
}
