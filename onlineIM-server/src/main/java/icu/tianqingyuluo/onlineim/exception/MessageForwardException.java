package icu.tianqingyuluo.onlineim.exception;

import org.springframework.http.HttpStatus;

/**
 * 转发被拒绝：参数不合法 400、无权访问来源会话 / 目标不是好友或所在群 403、
 * 来源消息不存在 404、来源消息已撤回 409。整次请求被拒绝，不会产生任何消息。
 */
public class MessageForwardException extends RuntimeException {
    private final HttpStatus status;

    public MessageForwardException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static MessageForwardException badRequest(String message) {
        return new MessageForwardException(HttpStatus.BAD_REQUEST, message);
    }

    public static MessageForwardException forbidden(String message) {
        return new MessageForwardException(HttpStatus.FORBIDDEN, message);
    }

    public static MessageForwardException notFound(String message) {
        return new MessageForwardException(HttpStatus.NOT_FOUND, message);
    }

    public static MessageForwardException recalled(String message) {
        return new MessageForwardException(HttpStatus.CONFLICT, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
