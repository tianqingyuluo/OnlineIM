package icu.tianqingyuluo.onlineim.exception;

import org.springframework.http.HttpStatus;

/**
 * 草稿读写被拒绝。REST 由全局异常处理器转换为 4xx，body.code 为下面的错误码。
 */
public class DraftException extends RuntimeException {

    public static final String NOT_CONVERSATION_MEMBER = "NOT_CONVERSATION_MEMBER";
    public static final String DRAFT_CONTENT_INVALID = "DRAFT_CONTENT_INVALID";

    private final HttpStatus status;
    private final String code;

    public DraftException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DraftException notMember() {
        return new DraftException(HttpStatus.FORBIDDEN, NOT_CONVERSATION_MEMBER, "无权访问该会话");
    }

    public static DraftException contentTooLong(int max) {
        return new DraftException(HttpStatus.BAD_REQUEST, DRAFT_CONTENT_INVALID,
                "草稿不能超过" + max + "个字符");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
