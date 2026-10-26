package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.response.IceServersResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.call.CallIceService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallControllerTest {

    @Test
    void iceServersRequireAuthentication() throws Exception {
        CallIceService ice = mock(CallIceService.class);
        IceServersResponse body = IceServersResponse.builder()
                .callTimeoutSeconds(45)
                .iceServers(List.of(IceServersResponse.IceServerItem.builder()
                        .urls(List.of("stun:stun.l.google.com:19302")).build()))
                .build();
        when(ice.forUser("usr_me")).thenReturn(body);
        CallController controller = new CallController(ice);
        UserIDProvider principal = () -> "usr_me";

        assertEquals(401, controller.iceServers(null).getStatusCode().value());
        assertEquals(body, controller.iceServers(principal).getBody());
        verify(ice).forUser("usr_me");

        RequestMapping type = CallController.class.getAnnotation(RequestMapping.class);
        assertEquals("/api/v1/calls", type.value()[0]);
        Method endpoint = CallController.class.getMethod("iceServers", UserIDProvider.class);
        GetMapping mapping = endpoint.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertEquals("/ice-servers", mapping.value()[0]);
    }
}
