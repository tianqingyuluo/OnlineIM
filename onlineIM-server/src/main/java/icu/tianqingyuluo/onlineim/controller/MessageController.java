package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 消息管理接口
 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    /**
     * 发送私聊消息
     * @param request 消息请求
     * @return 发送结果
     */
    @PostMapping("/private")
    public ResponseEntity<MessageResponse> sendPrivateMessage(@RequestBody MessageSendRequest request) {
        // TODO: 实现发送私聊消息逻辑
        return null;
    }
    
    /**
     * 发送群聊消息
     * @param request 消息请求
     * @return 发送结果
     */
    @PostMapping("/group")
    public ResponseEntity<MessageResponse> sendGroupMessage(@RequestBody MessageSendRequest request) {
        // TODO: 实现发送群聊消息逻辑
        return null;
    }
    
    /**
     * 获取私聊历史消息
     * @param userId 用户ID
     * @param timestamp 时间戳（可选）
     * @param size 消息数量（可选）
     * @return 消息列表
     */
    @GetMapping("/private/{userId}")
    public ResponseEntity<List<MessageResponse>> getPrivateHistory(
            @PathVariable String userId,
            @RequestParam(required = false) Long timestamp,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        // TODO: 实现获取私聊历史消息逻辑
        return null;
    }
    
    /**
     * 获取群聊历史消息
     * @param groupId 群组ID
     * @param timestamp 时间戳（可选）
     * @param size 消息数量（可选）
     * @return 消息列表
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<MessageResponse>> getGroupHistory(
            @PathVariable String groupId,
            @RequestParam(required = false) Long timestamp,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        // TODO: 实现获取群聊历史消息逻辑
        return null;
    }
    
    /**
     * 撤回消息
     * @param messageId 消息ID
     * @return 撤回结果
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<Map<String, String>> recallMessage(@PathVariable String messageId) {
        // TODO: 实现撤回消息逻辑
        return null;
    }
    
    /**
     * 标记消息已读
     * @param messageIds 消息ID列表
     * @return 标记结果
     */
    @PostMapping("/read")
    public ResponseEntity<Map<String, String>> markAsRead(@RequestBody List<String> messageIds) {
        // TODO: 实现标记消息已读逻辑
        return null;
    }
    
    /**
     * 上传文件（图片、语音、视频、文档等）
     * @param type 文件类型：image, voice, video, file
     * @param file 文件内容
     * @return 上传结果，包含文件URL等信息
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam String type,
            @RequestParam("file") MultipartFile file) {
        // TODO: 实现上传文件逻辑
        return null;
    }
    
    /**
     * 获取消息同步序列号
     * @return 最新序列号
     */
    @GetMapping("/sync/sequence")
    public ResponseEntity<Map<String, Long>> getSequence() {
        // TODO: 实现获取消息同步序列号逻辑
        return null;
    }
    
    /**
     * 增量同步消息
     * @param sequence 客户端当前序列号
     * @return 增量消息列表
     */
    @GetMapping("/sync")
    public ResponseEntity<List<MessageResponse>> syncMessages(@RequestParam Long sequence) {
        // TODO: 实现增量同步消息逻辑
        return null;
    }
} 