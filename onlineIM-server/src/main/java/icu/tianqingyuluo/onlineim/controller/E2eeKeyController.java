package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.E2eeKeyUploadRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.E2eeKeyResponse;
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
 * 端到端加密公钥目录。只接收公钥。
 */
@RestController
@RequestMapping("/api/v1/e2ee/keys")
public class E2eeKeyController {

    private final E2eeService e2eeService;

    public E2eeKeyController(E2eeService e2eeService) {
        this.e2eeService = e2eeService;
    }

    @PutMapping
    public ResponseEntity<E2eeKeyResponse> upsert(
            @Valid @RequestBody E2eeKeyUploadRequest request,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(e2eeService.upsertKey(principal.getID(), request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<E2eeKeyResponse> get(
            @PathVariable("userId") String userId,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(e2eeService.getKey(principal.getID(), userId));
    }
}
