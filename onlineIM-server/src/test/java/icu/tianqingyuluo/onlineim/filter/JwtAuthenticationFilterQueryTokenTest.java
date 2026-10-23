package icu.tianqingyuluo.onlineim.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterQueryTokenTest {

    @Test
    void onlyChatFilePathsAcceptQueryTokens() {
        assertTrue(JwtAuthenticationFilter.isChatFileRequest(new MockHttpServletRequest("GET", "/onlineim/chats/images/a.png")));
        assertFalse(JwtAuthenticationFilter.isChatFileRequest(new MockHttpServletRequest("GET", "/onlineim/avatars/a.png")));
        assertFalse(JwtAuthenticationFilter.isChatFileRequest(new MockHttpServletRequest("GET", "/api/v1/friends")));
    }
}
