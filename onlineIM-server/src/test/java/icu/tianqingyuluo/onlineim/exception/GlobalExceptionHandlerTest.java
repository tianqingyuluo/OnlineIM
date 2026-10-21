package icu.tianqingyuluo.onlineim.exception;

import icu.tianqingyuluo.onlineim.pojo.dto.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unknownRouteIs404() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new NoResourceFoundException(HttpMethod.GET, "api/v1/nope"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals("404", response.getBody().getCode());
        assertEquals("接口不存在", response.getBody().getMessage());
    }

    @Test
    void wrongMethodIs405WithAllowHeader() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new HttpRequestMethodNotSupportedException("PUT", List.of("POST")));
        assertEquals(405, response.getStatusCode().value());
        assertTrue(response.getHeaders().getAllow().contains(HttpMethod.POST));
    }

    @Test
    void unsupportedMediaTypeIs415AndMissingParamIs400() {
        assertEquals(415, handler.handleGeneralException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)))
                .getStatusCode().value());
        ResponseEntity<ErrorResponse> missing = handler.handleGeneralException(
                new MissingServletRequestParameterException("seq_id", "String"));
        assertEquals(400, missing.getStatusCode().value());
        assertEquals("缺少请求参数: seq_id", missing.getBody().getMessage());
    }

    @Test
    void unexpectedErrorsStay500() {
        assertEquals(500, handler.handleGeneralException(new RuntimeException("boom")).getStatusCode().value());
    }

    @Test
    void recallErrorsUseTheirOwnStatusAndCode() {
        ResponseEntity<ErrorResponse> response = handler.handleMessageRecall(MessageRecallException.windowExpired("2分钟"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals(MessageRecallException.RECALL_WINDOW_EXPIRED, response.getBody().getCode());
    }
}
