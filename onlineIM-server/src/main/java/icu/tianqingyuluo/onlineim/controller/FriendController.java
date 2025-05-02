package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendRequestCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友关系管理接口
 */
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    /**
     * 获取好友列表
     * @return 好友列表
     */
    @GetMapping("")
    public ResponseEntity<List<FriendResponse>> getFriends() {
        // TODO: 实现获取好友列表逻辑
        return null;
    }
    
    /**
     * 获取好友详情
     * @param friendId 好友ID
     * @return 好友详情
     */
    @GetMapping("/{friendId}")
    public ResponseEntity<FriendResponse> getFriend(@PathVariable String friendId) {
        // TODO: 实现获取好友详情逻辑
        return null;
    }
    
    /**
     * 更新好友备注
     * @param friendId 好友ID
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{friendId}/remark")
    public ResponseEntity<Map<String, String>> updateRemark(@PathVariable String friendId, @RequestBody Map<String, String> request) {
        // TODO: 实现更新好友备注逻辑
        return null;
    }
    
    /**
     * 更新好友分组
     * @param friendId 好友ID
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{friendId}/group")
    public ResponseEntity<Map<String, String>> updateFriendGroup(@PathVariable String friendId, @RequestBody Map<String, String> request) {
        // TODO: 实现更新好友分组逻辑
        return null;
    }
    
    /**
     * 删除好友
     * @param friendId 好友ID
     * @return 删除结果
     */
    @DeleteMapping("/{friendId}")
    public ResponseEntity<Map<String, String>> deleteFriend(@PathVariable String friendId) {
        // TODO: 实现删除好友逻辑
        return null;
    }
    
    /**
     * 拉黑好友
     * @param friendId 好友ID
     * @return 操作结果
     */
    @PutMapping("/{friendId}/block")
    public ResponseEntity<Map<String, String>> blockFriend(@PathVariable String friendId) {
        // TODO: 实现拉黑好友逻辑
        return null;
    }
    
    /**
     * 取消拉黑好友
     * @param friendId 好友ID
     * @return 操作结果
     */
    @DeleteMapping("/{friendId}/block")
    public ResponseEntity<Map<String, String>> unblockFriend(@PathVariable String friendId) {
        // TODO: 实现取消拉黑好友逻辑
        return null;
    }
    
    /**
     * 获取黑名单列表
     * @return 黑名单列表
     */
    @GetMapping("/blacklist")
    public ResponseEntity<List<UserBriefResponse>> getBlacklist() {
        // TODO: 实现获取黑名单列表逻辑
        return null;
    }
    
    /**
     * 发送好友请求
     * @param request 好友请求
     * @return 请求结果
     */
    @PostMapping("/requests")
    public ResponseEntity<Map<String, String>> sendFriendRequest(@RequestBody FriendRequestCreateRequest request) {
        // TODO: 实现发送好友请求逻辑
        return null;
    }
    
    /**
     * 获取收到的好友请求
     * @return 好友请求列表
     */
    @GetMapping("/requests/received")
    public ResponseEntity<List<FriendRequestResponse>> getReceivedFriendRequests() {
        // TODO: 实现获取收到的好友请求逻辑
        return null;
    }
    
    /**
     * 获取发送的好友请求
     * @return 好友请求列表
     */
    @GetMapping("/requests/sent")
    public ResponseEntity<List<FriendRequestResponse>> getSentFriendRequests() {
        // TODO: 实现获取发送的好友请求逻辑
        return null;
    }
    
    /**
     * 接受好友请求
     * @param requestId 请求ID
     * @return 操作结果
     */
    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<Map<String, String>> acceptFriendRequest(@PathVariable String requestId) {
        // TODO: 实现接受好友请求逻辑
        return null;
    }
    
    /**
     * 拒绝好友请求
     * @param requestId 请求ID
     * @return 操作结果
     */
    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<Map<String, String>> rejectFriendRequest(@PathVariable String requestId) {
        // TODO: 实现拒绝好友请求逻辑
        return null;
    }
} 