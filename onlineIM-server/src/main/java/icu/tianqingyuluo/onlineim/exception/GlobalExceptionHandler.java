package icu.tianqingyuluo.onlineim.exception;

import icu.tianqingyuluo.onlineim.pojo.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("发生错误: {}", e.getMessage(), e);
        ErrorResponse errorResponse = new ErrorResponse("500", "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
