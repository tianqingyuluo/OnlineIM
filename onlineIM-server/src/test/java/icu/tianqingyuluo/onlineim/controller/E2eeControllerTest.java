package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.E2eeKeyUploadRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.E2eeSessionRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.E2eeKeyResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.E2eeSessionResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.E2eeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class E2eeControllerTest {

    @Test
    void keyAndSessionControllersDelegateToService() {
        E2eeService service = mock(E2eeService.class);
        E2eeKeyController keys = new E2eeKeyController(service);
        E2eeSessionController sessions = new E2eeSessionController(service);
        UserIDProvider me = () -> "usr_me";
        E2eeKeyUploadRequest upload = E2eeKeyUploadRequest.builder().keyId("key_me").build();
        E2eeSessionRequest toggle = E2eeSessionRequest.builder().enabled(true).build();
        when(service.upsertKey("usr_me", upload)).thenReturn(E2eeKeyResponse.builder().userId("usr_me").build());
        when(service.getKey("usr_me", "usr_peer")).thenReturn(E2eeKeyResponse.builder().userId("usr_peer").build());
        when(service.getSession("usr_me", "conv_1")).thenReturn(E2eeSessionResponse.builder().conversationId("conv_1").enabled(false).build());
        when(service.setSession("usr_me", "conv_1", toggle)).thenReturn(E2eeSessionResponse.builder().enabled(true).build());
        when(service.setSessionByPeer("usr_me", "usr_peer", toggle)).thenReturn(E2eeSessionResponse.builder().enabled(true).build());

        assertEquals(HttpStatus.OK, keys.upsert(upload, me).getStatusCode());
        assertEquals("usr_peer", keys.get("usr_peer", me).getBody().getUserId());
        assertEquals(Boolean.FALSE, sessions.get("conv_1", me).getBody().getEnabled());
        assertEquals(Boolean.TRUE, sessions.put("conv_1", toggle, me).getBody().getEnabled());
        assertEquals(Boolean.TRUE, sessions.putByPeer("usr_peer", toggle, me).getBody().getEnabled());
        assertEquals(HttpStatus.UNAUTHORIZED, keys.upsert(upload, null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, sessions.put("conv_1", toggle, null).getStatusCode());
        verify(service).setSessionByPeer("usr_me", "usr_peer", toggle);
    }
}
