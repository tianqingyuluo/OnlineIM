package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.pojo.dto.request.ConversationCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageReadersResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReadStateResponse;
import icu.tianqingyuluo.onlineim.service.ConversationReadStateService;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import icu.tianqingyuluo.onlineim.service.ConversationService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.util.ConversationIdUtil;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import jakarta.validation.Valid;
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
    private final GroupMemberService groupMemberService;
    private final ConversationReadStateService readStateService;
    private final FriendService friendService;

    @Autowired
    public ConversationController(ConversationService conversationService,
                                  JwtUtil jwtUtil,
                                  GroupMemberService groupMemberService,
                                  ConversationReadStateService readStateService,
                                  FriendService friendService) {
        this.conversationService = conversationService;
        this.jwtUtil = jwtUtil;
        this.groupMemberService = groupMemberService;
        this.readStateService = readStateService;
        this.friendService = friendService;
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

    /**
     * 打开/创建会话。幂等：只确保当前用户自己的记录存在并显示，不会重置自己或他人的置顶、免打扰、未读。
     * 群会话要求调用者是群成员；私聊要求双方是好友。
     */
    @PostMapping("/create")
    public ResponseEntity<?> createConversation(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody ConversationCreateRequest request
    ) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String targetId = request.getTargetId();
        if ("group".equals(request.getType())) {
            if (!targetId.startsWith("grp_") || !groupMemberService.isGroupMember(targetId, userId)) {
                throw new ForbiddenException("只有群成员才能打开群会话");
            }
            return ResponseEntity.ok(conversationService.openConversation(
                    targetId, ConversationRecordService.TYPE_GROUP, userId, targetId));
        }
        if (userId.equals(targetId) || !friendService.areFriends(userId, targetId)) {
            throw new ForbiddenException("只能和好友创建私聊会话");
        }
        return ResponseEntity.ok(conversationService.openConversation(
                ConversationIdUtil.privateConversationId(userId, targetId),
                ConversationRecordService.TYPE_PRIVATE, userId, targetId));
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
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ReadStateResponse response = readStateService.getReadState(conversationId, userId, seqId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversation_id", response.getConversationId());
        result.put("from_seq_id", seqId);
        result.put("read_seq", response.getReadSeq());
        result.put("latest_seq", response.getLatestSeq());
        result.put("unread_count", response.getUnreadCount());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取当前用户在会话中的送达/已读游标，用于首次加载和重连恢复。
     */
    @GetMapping("/{conversationId}/read-state")
    public ResponseEntity<ReadStateResponse> getReadState(
            @RequestHeader("Authorization") String token,
            @PathVariable String conversationId) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(readStateService.getReadState(conversationId, userId, null));
    }

    /**
     * 获取消息的阅读成员明细。
     */
    @GetMapping("/{conversationId}/messages/{messageId}/readers")
    public ResponseEntity<MessageReadersResponse> getMessageReaders(
            @RequestHeader("Authorization") String token,
            @PathVariable String conversationId,
            @PathVariable String messageId) {
        String userId = jwtUtil.getUserIDFromToken(token);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        MessageReadersResponse response = readStateService.getMessageReaders(conversationId, messageId, userId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
