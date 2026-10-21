package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.response.DeviceSessionResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.DeviceSessionService;
import icu.tianqingyuluo.onlineim.util.DeviceTypes;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前账号的登录设备。不放在 /auth 下，避免被当成匿名接口。
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceSessionController {

    private final DeviceSessionService deviceSessionService;
    private final JwtUtil jwtUtil;

    public DeviceSessionController(DeviceSessionService deviceSessionService, JwtUtil jwtUtil) {
        this.deviceSessionService = deviceSessionService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal UserIDProvider principal,
                                    @RequestHeader("Authorization") String authorization) {
        String currentDeviceId = jwtUtil.getDeviceIDFromToken(authorization);
        List<DeviceSessionResponse> devices = deviceSessionService.list(principal.getID(), currentDeviceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("devices", devices);
        return body;
    }

    @PostMapping("/{deviceId}/logout")
    public ResponseEntity<?> logout(@AuthenticationPrincipal UserIDProvider principal,
                                    @PathVariable String deviceId) {
        if (!DeviceTypes.isSafeDeviceId(deviceId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "设备标识无效"));
        }
        if (!deviceSessionService.remoteLogout(principal.getID(), deviceId)) {
            return ResponseEntity.status(404).body(Map.of("message", "设备不存在或已下线"));
        }
        return ResponseEntity.noContent().build();
    }
}
