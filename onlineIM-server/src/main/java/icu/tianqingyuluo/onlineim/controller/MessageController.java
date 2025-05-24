package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.service.MessageService;
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

    @Autowired
    public MessageController(MessageService messageService, JwtUtil jwtUtil) {
        this.messageService = messageService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 获取向上滚动的历史消息
     * @param conversationId 会话ID
     * @param seqId 消息序列号 (可选)
     * @param size 消息数量（可选）
     * @return 消息列表
     */
    @GetMapping("/private/{conversationId}")
    public ResponseEntity<List<MessageResponse>> getPrivateHistory(
            @PathVariable String conversationId,
            @RequestParam(name = "seq_id", required = false) String seqId,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        List<MessageResponse> messages = messageService.getPrivateHistory(conversationId, seqId, size, userId);
        return ResponseEntity.ok(messages);
    }
    
    /**
     * 获取群聊历史消息
     * @param groupId 群组ID
     * @param seqId 消息序列号（可选）
     * @param size 消息数量（可选）
     * @return 消息列表
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<MessageResponse>> getGroupHistory(
            @PathVariable String groupId,
            @RequestParam(required = false) String seqId,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        List<MessageResponse> messages = messageService.getGroupHistory(groupId, seqId, size, userId);
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean success = messageService.recallMessage(messageId, userId);
        if (!success) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "消息已撤回");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 标记消息已读
     * @param messageIds 消息ID列表
     * @return 标记结果
     */
    @PostMapping("/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @RequestBody List<String> messageIds,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean success = messageService.markAsRead(messageIds, userId);
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "消息已标记为已读");
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
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
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
    @GetMapping("/sync")
    public ResponseEntity<List<MessageResponse>> syncMessages(
            @RequestParam("seq_id") String seqId,
            @RequestHeader("Authorization") String token) {
        
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        List<MessageResponse> messages = messageService.syncMessages(seqId, userId);
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