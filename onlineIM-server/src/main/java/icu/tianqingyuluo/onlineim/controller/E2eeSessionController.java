package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.E2eeSessionRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.E2eeSessionResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.E2eeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 私聊端到端加密开关。群聊会在服务层被拒绝。
 */
@RestController
public class E2eeSessionController {

    private final E2eeService e2eeService;

    public E2eeSessionController(E2eeService e2eeService) {
        this.e2eeService = e2eeService;
    }

    @GetMapping("/api/v1/conversations/{conversationId}/e2ee")
    public ResponseEntity<E2eeSessionResponse> get(
            @PathVariable("conversationId") String conversationId,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(e2eeService.getSession(principal.getID(), conversationId));
    }

    @PutMapping("/api/v1/conversations/{conversationId}/e2ee")
    public ResponseEntity<E2eeSessionResponse> put(
            @PathVariable("conversationId") String conversationId,
            @Valid @RequestBody E2eeSessionRequest request,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(e2eeService.setSession(principal.getID(), conversationId, request));
    }

    @PutMapping("/api/v1/e2ee/sessions/{targetUserId}")
    public ResponseEntity<E2eeSessionResponse> putByPeer(
            @PathVariable("targetUserId") String targetUserId,
            @Valid @RequestBody E2eeSessionRequest request,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(e2eeService.setSessionByPeer(principal.getID(), targetUserId, request));
    }
}
