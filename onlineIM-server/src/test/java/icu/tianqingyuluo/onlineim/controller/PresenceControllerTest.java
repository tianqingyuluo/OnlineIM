package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.PresenceSettingsRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceQueryResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceSettingsResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceControllerTest {

    private final PresenceService presenceService = mock(PresenceService.class);
    private final PresenceController controller = new PresenceController(presenceService);
    private final UserIDProvider principal = () -> "usr_me";

    @Test
    void queryUsesAuthenticatedUserAndForwardsIds() throws Exception {
        PresenceQueryResponse expected = PresenceQueryResponse.builder().presences(List.of()).build();
        when(presenceService.query("usr_me", "usr_a,usr_b")).thenReturn(expected);

        ResponseEntity<PresenceQueryResponse> response = controller.query("usr_a,usr_b", principal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(presenceService).query("usr_me", "usr_a,usr_b");
        assertEquals(401, controller.query("usr_a", null).getStatusCode().value());

        RequestMapping type = PresenceController.class.getAnnotation(RequestMapping.class);
        assertEquals("/api/v1/presence", type.value()[0]);
        Method endpoint = PresenceController.class.getMethod("query", String.class, UserIDProvider.class);
        assertNotNull(endpoint.getAnnotation(GetMapping.class));
    }

    @Test
    void settingsUseAuthenticatedUser() throws Exception {
        when(presenceService.getSettings("usr_me"))
                .thenReturn(PresenceSettingsResponse.builder().hideOnlineStatus(false).build());
        when(presenceService.updateSettings("usr_me", true))
                .thenReturn(PresenceSettingsResponse.builder().hideOnlineStatus(true).build());

        assertEquals(200, controller.getSettings(principal).getStatusCode().value());
        assertEquals(401, controller.getSettings(null).getStatusCode().value());
        ResponseEntity<PresenceSettingsResponse> updated = controller.updateSettings(
                new PresenceSettingsRequest(true), principal);
        assertEquals(true, updated.getBody().isHideOnlineStatus());
        assertEquals(401, controller.updateSettings(new PresenceSettingsRequest(true), null).getStatusCode().value());

        Method endpoint = PresenceController.class.getMethod("updateSettings", PresenceSettingsRequest.class, UserIDProvider.class);
        PutMapping mapping = endpoint.getAnnotation(PutMapping.class);
        assertEquals("/settings", mapping.value()[0]);
    }
}
