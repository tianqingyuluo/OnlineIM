package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.document.Conversation;
import icu.tianqingyuluo.onlineim.pojo.dto.request.ConversationCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import icu.tianqingyuluo.onlineim.repository.ConversationRepository;
import icu.tianqingyuluo.onlineim.service.ConversationService;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 会话管理接口
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final JwtUtil jwtUtil;
    private final ConversationRepository conversationRepository;

    @Autowired
    public ConversationController(ConversationService conversationService, JwtUtil jwtUtil, ConversationRepository conversationRepository) {
        this.conversationService = conversationService;
        this.jwtUtil = jwtUtil;
        this.conversationRepository = conversationRepository;
    }

    /**
     * 获取会话列表
     * @return 会话列表
     */
    @GetMapping("")
    public ResponseEntity<List<ConversationResponse>> getConversations(
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        List<ConversationResponse> response = conversationService.getUserConversations(userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取会话详情
     * @param conversationId 会话ID
     * @return 会话详情
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(
            @PathVariable String conversationId,
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        ConversationResponse response = conversationService.getConversation(conversationId, userId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除会话
     * @param conversationId 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Map<String, String>> deleteConversation(
            @PathVariable String conversationId,
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean success = conversationService.deleteConversation(conversationId, userId);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "会话已删除");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/create")
    public ResponseEntity<?> createConversation(
            @RequestHeader("Authorization") String token,
            @RequestBody ConversationCreateRequest request
    ) {
        String userId = jwtUtil.getUserIDFromToken(token);

        Conversation conversation = new Conversation();
        Conversation.LastMessage lastMessage = new Conversation.LastMessage();
        if (request.getType().equals("group")) {
            conversation.setId(request.getTargetId());  // coversationId 此时就等于 群id
            conversation.setUserId(userId);
            conversation.setTargetId(request.getTargetId());
            conversation.setConversationType(request.getType());
            conversation.setUnreadCount(0);
            conversation.setLastMessage(lastMessage);
            conversation.setTop(false);
            conversation.setMute(false);
            conversation.setStatus(1);
            conversation.setUpdatedAt(new Date());
        }
        else {
            conversation.setId("conv_" + ( (userId.compareTo(request.getTargetId())) < 0
                    ? userId + request.getTargetId() :
                    request.getTargetId() + userId) ); // 谁id字典序小谁在前面
            conversation.setUserId(userId);
            conversation.setTargetId(request.getTargetId());
            conversation.setConversationType(request.getType());
            conversation.setUnreadCount(0);
            conversation.setLastMessage(lastMessage);
            conversation.setTop(false);
            conversation.setMute(false);
            conversation.setStatus(1);
            conversation.setUpdatedAt(new Date());
        }
        conversationRepository.save(conversation);

        ConversationResponse conversationResponse = conversationService.convertToConversationResponse(conversation, userId);

        return ResponseEntity.ok(conversationResponse);
    }

    /**
     * 置顶会话
     * @param conversationId 会话ID
     * @return 置顶结果
     */
    @PostMapping("/{conversationId}/top")
    public ResponseEntity<Map<String, String>> topConversation(
            @PathVariable String conversationId,
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean success = conversationService.topConversation(conversationId, userId);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "会话已置顶");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 取消置顶会话
     * @param conversationId 会话ID
     * @return 取消置顶结果
     */
    @DeleteMapping("/{conversationId}/top")
    public ResponseEntity<Map<String, String>> untopConversation(
            @PathVariable String conversationId,
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean success = conversationService.untopConversation(conversationId, userId);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "已取消置顶");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 设置会话免打扰
     * @param conversationId 会话ID
     * @return 设置结果
     */
    @PostMapping("/{conversationId}/mute")
    public ResponseEntity<Map<String, String>> muteConversation(
            @PathVariable String conversationId,
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean success = conversationService.muteConversation(conversationId, userId);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "已设置免打扰");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 取消会话免打扰
     * @param conversationId 会话ID
     * @return 取消结果
     */
    @DeleteMapping("/{conversationId}/mute")
    public ResponseEntity<Map<String, String>> unmuteConversation(
            @PathVariable String conversationId,
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean success = conversationService.unmuteConversation(conversationId, userId);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "已取消免打扰");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 清空会话消息
     * @param conversationId 会话ID
     * @return 清空结果
     */
    @DeleteMapping("/{conversationId}/messages")
    public ResponseEntity<Map<String, String>> clearMessages(
            @PathVariable String conversationId,
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        boolean success = conversationService.clearMessages(conversationId, userId);
        if (!success) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "会话消息已清空");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取未读消息数
     * @return 未读消息数信息
     */
    @GetMapping("/{conversationId}/unread-count/{seqId}")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @RequestHeader("Authorization") String token,
            @PathVariable String conversationId,
            @PathVariable String seqId) {
//        String userId = jwtUtil.getUserIDFromToken(token);
//        if (userId == null) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
//        }
//
//        Map<String, Object> result = conversationService.getUnreadCount(userId);
//        return ResponseEntity.ok(result);
        return null;
        // 需要修改成根据seqId拿到该conversation的seqId后面的消息数统计
    }
}