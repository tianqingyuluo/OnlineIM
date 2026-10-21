package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageEditRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageForwardRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageForwardResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageContextResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.pojo.document.ChatUpload;
import icu.tianqingyuluo.onlineim.service.ChatMediaService;
import icu.tianqingyuluo.onlineim.service.MessageEditService;
import icu.tianqingyuluo.onlineim.service.MessageForwardService;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息管理接口
 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;
    private final JwtUtil jwtUtil;
    private final ChatMediaService chatMediaService;
    private final MessageForwardService messageForwardService;
    private final MessageEditService messageEditService;

    public MessageController(MessageService messageService, JwtUtil jwtUtil, ChatMediaService chatMediaService,
                             MessageForwardService messageForwardService, MessageEditService messageEditService) {
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
        this.chatMediaService = chatMediaService;
        this.messageForwardService = messageForwardService;
        this.messageEditService = messageEditService;
    }

    /**
     * 获取会话历史消息，支持群聊和单聊
     * @param conversationId 会话ID（可以是群聊ID或单聊ID）
     * @param seqId 消息序列号 (可选)
     * @param size 消息数量（可选）
     * @return 消息列表
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getHistory(
            @PathVariable String conversationId,
            @RequestParam(name = "seq_id", required = false) String seqId,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorCodeUtil.getErrorOutput("401", "权限不足"));
        }
        
        List<MessageResponse> messages = messageService.getHistory(conversationId, seqId, size, userId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("messages", messages);
        result.put("has_more_before", messages.size() >= size);
        result.put("has_more_after", false);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{conversationId}/{messageId}/context")
    public ResponseEntity<MessageContextResponse> getContext(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @RequestParam(required = false, defaultValue = "20") Integer before,
            @RequestParam(required = false, defaultValue = "20") Integer after,
            @AuthenticationPrincipal UserIDProvider principal) {
        return ResponseEntity.ok(messageService.getContext(
                conversationId, messageId, before, after, principal.getID()));
    }
    
    /**
     * 撤回消息
     * @param messageId 消息ID
     * @return 撤回结果
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Map<String, String>> recallMessage(
            @PathVariable String messageId,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorCodeUtil.getErrorOutput("401", "权限不足"));
        }
        
        boolean success = messageService.recallMessage(messageId, userId);
        if (!success) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "权限不足"));
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "消息已撤回");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 编辑自己发送的文本消息（时间窗内），返回编辑后的消息；会话成员经 WebSocket 收到 MESSAGE_EDITED
     */
    @PutMapping("/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable String messageId,
            @RequestBody MessageEditRequest request,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(messageEditService.edit(messageId, request, principal.getID()));
    }

    // 已删除标记消息已读功能
    
    /**
     * 获取需要撤回的消息序列号列表
     * @param conversationId 会话ID
     * @param seqId 客户端当前序列号
     * @return 需要撤回的消息序列号列表
     */
    @GetMapping("/recalls/{conversationId}")
    public ResponseEntity<?> getRecallList(
            @PathVariable String conversationId,
            @RequestParam("seq_id") String seqId,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorCodeUtil.getErrorOutput("401", "权限不足"));
        }
        
        List<String> recalls = messageService.getRecallList(conversationId, seqId, userId);
        Map<String, List<String>> result = new HashMap<>();
        result.put("recalls", recalls);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 上传聊天图片/文件/语音（multipart：type=image|file|voice，file=文件；语音可带 duration 秒或 duration_ms）。
     * 图片按内容识别格式并生成缩略图；返回 file_id 和 url、thumbnail_url、name、size、mime、width、height。
     * 发送图片/文件消息时 content 填 file_id（或含 file_id 的 JSON），服务端据此补全元数据。
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestHeader("Authorization") String token,
            @RequestParam String type,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "duration", required = false) String duration,
            @RequestParam(value = "duration_ms", required = false) String durationMs) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ChatUpload upload = chatMediaService.upload(userId, type, file, parseClientDuration(duration, durationMs));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.putAll(chatMediaService.toMediaMap(upload));
        return ResponseEntity.ok(result);
    }

    /** 语音客户端时长：duration_ms（毫秒）优先，其次 duration（秒，可带小数）；非法值按 400 处理 */
    static Long parseClientDuration(String duration, String durationMs) {
        try {
            if (durationMs != null && !durationMs.isBlank()) {
                return Math.round(Double.parseDouble(durationMs.trim()));
            }
            if (duration != null && !duration.isBlank()) {
                return Math.round(Double.parseDouble(duration.trim()) * 1000);
            }
        } catch (NumberFormatException e) {
            throw icu.tianqingyuluo.onlineim.exception.ChatMediaException.badRequest("duration 必须是数字");
        }
        return null;
    }

    /**
     * 增量同步消息
     * @param seqId 客户端当前序列号
     * @return 增量消息列表
     */
    @GetMapping("/sync/{conversationId}")
    public ResponseEntity<?> syncMessages(
            @PathVariable String conversationId,
            @RequestParam("seq_id") String seqId,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorCodeUtil.getErrorOutput("401", "权限不足"));
        }
        
        List<MessageResponse> messages = messageService.syncMessages(conversationId, seqId, userId);
        return ResponseEntity.ok(messages);
    }
    
    /**
     * 发送消息
     * @param request 消息请求体
     * @return 发送结果
     */
    @PostMapping("/send")
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody MessageSendRequest request,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(messageService.sendMessage(request, principal.getID()));
    }

    /**
     * 转发消息：逐条（separate）或合并为聊天记录（merged）发送到一个或多个好友 / 已加入的群
     */
    @PostMapping("/forward")
    public ResponseEntity<MessageForwardResponse> forwardMessages(
            @RequestBody MessageForwardRequest request,
            @AuthenticationPrincipal UserIDProvider principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(messageForwardService.forward(request, principal.getID()));
    }
}
