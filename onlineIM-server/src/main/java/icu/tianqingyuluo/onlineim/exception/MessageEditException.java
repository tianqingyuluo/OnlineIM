package icu.tianqingyuluo.onlineim.exception;

import org.springframework.http.HttpStatus;

/**
 * 消息编辑被拒绝。REST 由全局异常处理器转换为对应的 4xx（body.code 为下面的错误码），
 * WebSocket 转换为 ERROR 帧（code 相同）。
 */
public class MessageEditException extends RuntimeException {
    public static final String MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND";
    public static final String NOT_MESSAGE_SENDER = "NOT_MESSAGE_SENDER";
    public static final String NOT_CONVERSATION_MEMBER = "NOT_CONVERSATION_MEMBER";
    public static final String MESSAGE_ALREADY_RECALLED = "MESSAGE_ALREADY_RECALLED";
    public static final String EDIT_NOT_SUPPORTED = "EDIT_NOT_SUPPORTED";
    public static final String EDIT_WINDOW_EXPIRED = "EDIT_WINDOW_EXPIRED";
    public static final String EDIT_CONTENT_INVALID = "EDIT_CONTENT_INVALID";
    public static final String EDIT_CONFLICT = "EDIT_CONFLICT";

    private final HttpStatus status;
    private final String code;

    public MessageEditException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static MessageEditException notFound() {
        return new MessageEditException(HttpStatus.NOT_FOUND, MESSAGE_NOT_FOUND, "消息不存在");
    }

    public static MessageEditException notSender() {
        return new MessageEditException(HttpStatus.FORBIDDEN, NOT_MESSAGE_SENDER, "只能编辑自己发送的消息");
    }

    public static MessageEditException notMember(String message) {
        return new MessageEditException(HttpStatus.FORBIDDEN, NOT_CONVERSATION_MEMBER, message);
    }

    public static MessageEditException recalled() {
        return new MessageEditException(HttpStatus.CONFLICT, MESSAGE_ALREADY_RECALLED, "消息已撤回，无法编辑");
    }

    public static MessageEditException notSupported() {
        return new MessageEditException(HttpStatus.BAD_REQUEST, EDIT_NOT_SUPPORTED, "只能编辑文本消息");
    }

    public static MessageEditException windowExpired(String windowText) {
        return new MessageEditException(HttpStatus.BAD_REQUEST, EDIT_WINDOW_EXPIRED,
                "消息发送已超过" + windowText + "，无法编辑");
    }

    public static MessageEditException invalidContent(String message) {
        return new MessageEditException(HttpStatus.BAD_REQUEST, EDIT_CONTENT_INVALID, message);
    }

    public static MessageEditException conflict() {
        return new MessageEditException(HttpStatus.CONFLICT, EDIT_CONFLICT, "消息刚刚被修改过，请重试");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
