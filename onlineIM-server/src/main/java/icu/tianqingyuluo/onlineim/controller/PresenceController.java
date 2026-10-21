package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.PresenceSettingsRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceQueryResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.PresenceSettingsResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.PresenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线状态查询与隐私开关。实时变更走 WebSocket {@code PRESENCE_UPDATE}。
 */
@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping
    public ResponseEntity<PresenceQueryResponse> query(
            @RequestParam(name = "user_ids", required = false) String userIds,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(presenceService.query(principal.getID(), userIds));
    }

    @GetMapping("/settings")
    public ResponseEntity<PresenceSettingsResponse> getSettings(@AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(presenceService.getSettings(principal.getID()));
    }

    @PutMapping("/settings")
    public ResponseEntity<PresenceSettingsResponse> updateSettings(
            @Valid @RequestBody PresenceSettingsRequest request,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(presenceService.updateSettings(principal.getID(), request.getHideOnlineStatus()));
    }
}
