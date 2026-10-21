package icu.tianqingyuluo.onlineim.exception;

import org.springframework.http.HttpStatus;

/**
 * 消息撤回被拒绝（消息不存在、已撤回、超过撤回时间窗等）。
 * REST 由全局异常处理器转换为对应的 4xx，WebSocket 转换为 ERROR 帧。
 */
public class MessageRecallException extends RuntimeException {
    public static final String MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND";
    public static final String MESSAGE_ALREADY_RECALLED = "MESSAGE_ALREADY_RECALLED";
    public static final String RECALL_WINDOW_EXPIRED = "RECALL_WINDOW_EXPIRED";

    private final HttpStatus status;
    private final String code;

    public MessageRecallException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static MessageRecallException notFound() {
        return new MessageRecallException(HttpStatus.NOT_FOUND, MESSAGE_NOT_FOUND, "消息不存在");
    }

    public static MessageRecallException alreadyRecalled() {
        return new MessageRecallException(HttpStatus.CONFLICT, MESSAGE_ALREADY_RECALLED, "消息已撤回");
    }

    public static MessageRecallException windowExpired(String windowText) {
        return new MessageRecallException(HttpStatus.BAD_REQUEST, RECALL_WINDOW_EXPIRED,
                "消息发送已超过" + windowText + "，无法撤回");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
