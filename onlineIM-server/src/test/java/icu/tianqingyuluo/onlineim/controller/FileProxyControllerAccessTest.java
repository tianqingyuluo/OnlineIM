package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.entity.UserDetail;
import icu.tianqingyuluo.onlineim.service.ChatFileAccessService;
import icu.tianqingyuluo.onlineim.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileProxyControllerAccessTest {

    private static final String KEY = "chats/images/202610/0123456789abcdef0123456789abcdef.png";

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void outsiderIsForbiddenWhileMemberCanDownload() throws Exception {
        FileStorageService storage = mock(FileStorageService.class);
        ChatFileAccessService access = mock(ChatFileAccessService.class);
        FileProxyController controller = new FileProxyController(storage, access, "onlineim");
        when(access.decide("usr_out", KEY)).thenReturn(ChatFileAccessService.ReadDecision.DENY);
        when(access.decide("usr_member", KEY)).thenReturn(ChatFileAccessService.ReadDecision.ALLOW);
        when(storage.downloadFile("onlineim", KEY)).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        authenticate("usr_out");
        ResponseEntity<byte[]> denied = controller.file(request(KEY));
        assertEquals(403, denied.getStatusCode().value());
        assertTrue(new String(denied.getBody()).contains("无权访问该文件"));
        verify(storage, never()).downloadFile(any(), any());

        authenticate("usr_member");
        ResponseEntity<byte[]> allowed = controller.file(request(KEY));
        assertEquals(200, allowed.getStatusCode().value());
        assertArrayEquals(new byte[]{1, 2, 3}, allowed.getBody());
        assertTrue(allowed.getHeaders().getCacheControl().contains("private"));
    }

    @Test
    void anonymousChatRequestIsUnauthorizedAndAvatarsStayPublic() throws Exception {
        FileStorageService storage = mock(FileStorageService.class);
        ChatFileAccessService access = mock(ChatFileAccessService.class);
        FileProxyController controller = new FileProxyController(storage, access, "onlineim");
        when(storage.downloadFile(eq("onlineim"), eq("avatars/u.png")))
                .thenReturn(new ByteArrayInputStream(new byte[]{9}));

        ResponseEntity<byte[]> anonymous = controller.file(request(KEY));
        assertEquals(401, anonymous.getStatusCode().value());
        verify(storage, never()).downloadFile(any(), eq(KEY));

        ResponseEntity<byte[]> avatar = controller.file(request("avatars/u.png"));
        assertEquals(200, avatar.getStatusCode().value());
        assertTrue(avatar.getHeaders().getCacheControl().contains("public"));
    }

    private static void authenticate(String userId) {
        UserDetail user = new UserDetail(userId, "password", List.of(), userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private static MockHttpServletRequest request(String objectName) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/onlineim/" + objectName);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/onlineim/**");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/onlineim/" + objectName);
        return request;
    }
}
