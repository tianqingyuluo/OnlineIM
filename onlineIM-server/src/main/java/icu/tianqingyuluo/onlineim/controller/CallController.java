package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.response.IceServersResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.call.CallIceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下发 WebRTC 的 ICE 服务器。媒体不经过本接口。
 */
@RestController
@RequestMapping("/api/v1/calls")
public class CallController {

    private final CallIceService callIceService;

    public CallController(CallIceService callIceService) {
        this.callIceService = callIceService;
    }

    @GetMapping("/ice-servers")
    public ResponseEntity<IceServersResponse> iceServers(@AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(callIceService.forUser(principal.getID()));
    }
}
