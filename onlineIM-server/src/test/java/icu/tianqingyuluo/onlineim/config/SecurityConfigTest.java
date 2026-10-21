package icu.tianqingyuluo.onlineim.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void missingTokenIsReportedAs401Json() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityConfig.jsonUnauthorizedEntryPoint().commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no token"));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"code\":\"401\""));
    }
}
