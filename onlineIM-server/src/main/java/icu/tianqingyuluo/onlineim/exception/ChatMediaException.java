package icu.tianqingyuluo.onlineim.exception;

import org.springframework.http.HttpStatus;

/**
 * 聊天文件/图片上传或引用被拒绝（类型不允许、超过大小、图片损坏、引用了不属于自己的上传等）。
 * REST 由全局异常处理器按 {@link #getStatus()} 返回 4xx；WebSocket 作为参数错误返回 ERROR 帧。
 */
public class ChatMediaException extends IllegalArgumentException {
    private final HttpStatus status;

    public ChatMediaException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ChatMediaException badRequest(String message) {
        return new ChatMediaException(HttpStatus.BAD_REQUEST, message);
    }

    public static ChatMediaException tooLarge(String message) {
        return new ChatMediaException(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    public static ChatMediaException unsupportedType(String message) {
        return new ChatMediaException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
