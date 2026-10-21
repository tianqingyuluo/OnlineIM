package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.response.DeviceSessionResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserDetail;
import icu.tianqingyuluo.onlineim.service.DeviceSessionService;
import icu.tianqingyuluo.onlineim.util.DeviceTypes;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceSessionControllerTest {

    @Test
    void listsDevicesOfTheAuthenticatedUser() {
        DeviceSessionService service = mock(DeviceSessionService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.getDeviceIDFromToken("Bearer token")).thenReturn("web-1");
        when(service.list("usr_1", "web-1")).thenReturn(List.of(DeviceSessionResponse.builder()
                .deviceId("web-1")
                .deviceType(DeviceTypes.WEB)
                .current(true)
                .online(true)
                .build()));
        DeviceSessionController controller = new DeviceSessionController(service, jwtUtil);

        Map<String, Object> body = controller.list(principal(), "Bearer token");

        @SuppressWarnings("unchecked")
        List<DeviceSessionResponse> devices = (List<DeviceSessionResponse>) body.get("devices");
        assertEquals(1, devices.size());
        assertEquals("web-1", devices.get(0).getDeviceId());
    }

    @Test
    void remoteLogoutRejectsUnknownAndUnsafeIds() {
        DeviceSessionService service = mock(DeviceSessionService.class);
        DeviceSessionController controller = new DeviceSessionController(service, mock(JwtUtil.class));
        when(service.remoteLogout("usr_1", "phone-1")).thenReturn(false);

        ResponseEntity<?> missing = controller.logout(principal(), "phone-1");
        ResponseEntity<?> unsafe = controller.logout(principal(), "../other");

        assertEquals(404, missing.getStatusCode().value());
        assertEquals(400, unsafe.getStatusCode().value());
        verify(service).remoteLogout("usr_1", "phone-1");
    }

    @Test
    void remoteLogoutReturnsNoContent() {
        DeviceSessionService service = mock(DeviceSessionService.class);
        when(service.remoteLogout("usr_1", "phone-1")).thenReturn(true);
        DeviceSessionController controller = new DeviceSessionController(service, mock(JwtUtil.class));

        ResponseEntity<?> response = controller.logout(principal(), "phone-1");

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals(204, response.getStatusCode().value());
    }

    private static UserDetail principal() {
        return new UserDetail("alice", "secret", List.of(new SimpleGrantedAuthority("ROLE_USER")), "usr_1");
    }
}
