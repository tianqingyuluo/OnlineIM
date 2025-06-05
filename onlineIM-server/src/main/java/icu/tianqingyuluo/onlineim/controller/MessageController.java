package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
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

    public MessageController(MessageService messageService, JwtUtil jwtUtil) {
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
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
        return ResponseEntity.ok(messages);
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
        
        List<String> recalls = messageService.getRecallList(conversationId, seqId);
        Map<String, List<String>> result = new HashMap<>();
        result.put("recalls", recalls);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 上传文件（图片、语音、视频、文档等）
     * @param type 文件类型：image, voice, video, file
     * @param file 文件内容
     * @return 上传结果，包含文件URL等信息
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestHeader("Authorization") String token,
            @RequestParam String type,
            @RequestParam("file") MultipartFile file) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorCodeUtil.getErrorOutput("401", "权限不足"));
        }
        
        Map<String, String> result = messageService.uploadFile(userId, type, file);
        if ("error".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
        
        return ResponseEntity.ok(result);
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
            @RequestBody MessageSendRequest request,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        MessageResponse response = messageService.sendMessage(request, userId);
        if (response == null) {
            return ResponseEntity.badRequest().build();
        }
        
        return ResponseEntity.ok(response);
    }
}