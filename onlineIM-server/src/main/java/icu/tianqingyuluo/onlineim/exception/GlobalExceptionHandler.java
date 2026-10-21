package icu.tianqingyuluo.onlineim.exception;

import icu.tianqingyuluo.onlineim.pojo.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("身份验证失败: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("401", "用户名或密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ErrorResponse> handlePersistenceException(PersistenceException e) {
        log.error("数据库持久化错误: {}", e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse("500", "数据库操作失败");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(UsernameConflictException.class)
    public ResponseEntity<ErrorResponse> handleUsernameConflictException(UsernameConflictException e) {
        log.warn("用户名冲突: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("409", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        // 收集所有字段校验错误信息，保留400客户端错误语义
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        ErrorResponse errorResponse = new ErrorResponse("400", detail.isEmpty() ? "参数校验失败" : detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("400", "请求体格式错误");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("400", "参数类型不匹配: " + e.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MessageContextUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMessageContextUnavailable(MessageContextUnavailableException e) {
        log.warn("消息上下文不可用: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ReplyTargetUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleReplyTargetUnavailable(ReplyTargetUnavailableException e) {
        log.warn("引用目标不可用: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("403", e.getMessage()));
    }

    @ExceptionHandler(ChatMediaException.class)
    public ResponseEntity<ErrorResponse> handleChatMedia(ChatMediaException e) {
        log.warn("聊天文件被拒绝: {}", e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(String.valueOf(e.getStatus().value()), e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("400", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MessageRecallException.class)
    public ResponseEntity<ErrorResponse> handleMessageRecall(MessageRecallException e) {
        log.warn("撤回被拒绝: {}", e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /**
     * Spring MVC 框架异常（路由不存在、方法不支持、媒体类型不支持、缺少参数等）都实现了
     * {@link org.springframework.web.ErrorResponse}，按其自带的状态码返回，而不是统一 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        if (e instanceof MissingRequestHeaderException headerError
                && "Authorization".equalsIgnoreCase(headerError.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("401", "未登录或缺少 Authorization 请求头"));
        }
        if (e instanceof org.springframework.web.ErrorResponse frameworkError) {
            int status = frameworkError.getStatusCode().value();
            log.warn("请求无法处理({}): {}", status, e.getMessage());
            return ResponseEntity.status(status)
                    .headers(frameworkError.getHeaders())
                    .body(new ErrorResponse(String.valueOf(status), frameworkErrorMessage(e, status)));
        }
        log.error("发生错误: {}", e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse("500", "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    static String frameworkErrorMessage(Exception e, int status) {
        if (e instanceof NoResourceFoundException || e instanceof NoHandlerFoundException) {
            return "接口不存在";
        }
        if (e instanceof HttpRequestMethodNotSupportedException methodError) {
            return "不支持的请求方法: " + methodError.getMethod();
        }
        if (e instanceof HttpMediaTypeNotSupportedException) {
            return "不支持的 Content-Type";
        }
        if (e instanceof HttpMediaTypeNotAcceptableException) {
            return "无法生成可接受的响应类型";
        }
        if (e instanceof MissingServletRequestParameterException paramError) {
            return "缺少请求参数: " + paramError.getParameterName();
        }
        if (e instanceof MissingRequestHeaderException headerError) {
            return "缺少请求头: " + headerError.getHeaderName();
        }
        HttpStatus httpStatus = HttpStatus.resolve(status);
        return httpStatus != null ? httpStatus.getReasonPhrase() : "请求无法处理";
    }
}
