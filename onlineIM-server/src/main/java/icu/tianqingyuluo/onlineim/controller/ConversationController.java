package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.response.ConversationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话管理接口
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    /**
     * 获取会话列表
     * @return 会话列表
     */
    @GetMapping("")
    public ResponseEntity<List<ConversationResponse>> getConversations() {
        // TODO: 实现获取会话列表逻辑
        return null;
    }
    
    /**
     * 获取会话详情
     * @param conversationId 会话ID
     * @return 会话详情
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable String conversationId) {
        // TODO: 实现获取会话详情逻辑
        return null;
    }
    
    /**
     * 删除会话
     * @param conversationId 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Map<String, String>> deleteConversation(@PathVariable String conversationId) {
        // TODO: 实现删除会话逻辑
        return null;
    }
    
    /**
     * 置顶会话
     * @param conversationId 会话ID
     * @return 置顶结果
     */
    @PostMapping("/{conversationId}/top")
    public ResponseEntity<Map<String, String>> topConversation(@PathVariable String conversationId) {
        // TODO: 实现置顶会话逻辑
        return null;
    }
    
    /**
     * 取消置顶会话
     * @param conversationId 会话ID
     * @return 取消置顶结果
     */
    @DeleteMapping("/{conversationId}/top")
    public ResponseEntity<Map<String, String>> untopConversation(@PathVariable String conversationId) {
        // TODO: 实现取消置顶会话逻辑
        return null;
    }
    
    /**
     * 设置会话免打扰
     * @param conversationId 会话ID
     * @return 设置结果
     */
    @PostMapping("/{conversationId}/mute")
    public ResponseEntity<Map<String, String>> muteConversation(@PathVariable String conversationId) {
        // TODO: 实现设置会话免打扰逻辑
        return null;
    }
    
    /**
     * 取消会话免打扰
     * @param conversationId 会话ID
     * @return 取消结果
     */
    @DeleteMapping("/{conversationId}/mute")
    public ResponseEntity<Map<String, String>> unmuteConversation(@PathVariable String conversationId) {
        // TODO: 实现取消会话免打扰逻辑
        return null;
    }
    
    /**
     * 清空会话消息
     * @param conversationId 会话ID
     * @return 清空结果
     */
    @DeleteMapping("/{conversationId}/messages")
    public ResponseEntity<Map<String, String>> clearMessages(@PathVariable String conversationId) {
        // TODO: 实现清空会话消息逻辑
        return null;
    }
    
    /**
     * 获取未读消息数
     * @return 未读消息数信息
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        // TODO: 实现获取未读消息数逻辑
        return null;
    }
} 