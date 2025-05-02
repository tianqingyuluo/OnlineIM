package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupJoinRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupJoinRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 群组管理接口
 */
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    /**
     * 创建群组
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping("")
    public ResponseEntity<GroupResponse> createGroup(@RequestBody GroupCreateRequest request) {
        // TODO: 实现创建群组逻辑
        return null;
    }
    
    /**
     * 获取群组详情
     * @param groupId 群组ID
     * @return 群组详情
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroupDetail(@PathVariable String groupId) {
        // TODO: 实现获取群组详情逻辑
        return null;
    }
    
    /**
     * 更新群组信息
     * @param groupId 群组ID
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{groupId}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable String groupId, @RequestBody GroupUpdateRequest request) {
        // TODO: 实现更新群组信息逻辑
        return null;
    }
    
    /**
     * 解散群组
     * @param groupId 群组ID
     * @return 解散结果
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Map<String, String>> disbandGroup(@PathVariable String groupId) {
        // TODO: 实现解散群组逻辑
        return null;
    }
    
    /**
     * 获取用户加入的群组列表
     * @return 群组列表
     */
    @GetMapping("/joined")
    public ResponseEntity<List<GroupResponse>> getJoinedGroups() {
        // TODO: 实现获取用户加入的群组列表逻辑
        return null;
    }
    
    /**
     * 上传群头像
     * @param groupId 群组ID
     * @param avatar 头像文件
     * @return 上传结果
     */
    @PostMapping("/{groupId}/avatar")
    public ResponseEntity<Map<String, String>> uploadGroupAvatar(@PathVariable String groupId, @RequestParam("file") MultipartFile avatar) {
        // TODO: 实现上传群头像逻辑
        return null;
    }
    
    /**
     * 获取群成员列表
     * @param groupId 群组ID
     * @return 成员列表
     */
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberResponse>> getGroupMembers(@PathVariable String groupId) {
        // TODO: 实现获取群成员列表逻辑
        return null;
    }
    
    /**
     * 邀请用户加入群组
     * @param groupId 群组ID
     * @param request 邀请请求
     * @return 邀请结果
     */
    @PostMapping("/{groupId}/invite")
    public ResponseEntity<Map<String, String>> inviteToGroup(@PathVariable String groupId, @RequestBody Map<String, List<String>> request) {
        // TODO: 实现邀请用户加入群组逻辑
        return null;
    }
    
    /**
     * 申请加入群组
     * @param groupId 群组ID
     * @param request 申请请求
     * @return 申请结果
     */
    @PostMapping("/{groupId}/join")
    public ResponseEntity<Map<String, String>> joinGroup(@PathVariable String groupId, @RequestBody GroupJoinRequest request) {
        // TODO: 实现申请加入群组逻辑
        return null;
    }
    
    /**
     * 退出群组
     * @param groupId 群组ID
     * @return 退出结果
     */
    @PostMapping("/{groupId}/quit")
    public ResponseEntity<Map<String, String>> quitGroup(@PathVariable String groupId) {
        // TODO: 实现退出群组逻辑
        return null;
    }
    
    /**
     * 搜索群组
     * @param keyword 关键词
     * @return 群组列表
     */
    @GetMapping("/search")
    public ResponseEntity<List<GroupResponse>> searchGroups(@RequestParam String keyword) {
        // TODO: 实现搜索群组逻辑
        return null;
    }
    
    /**
     * 获取群组加入请求
     * @param groupId 群组ID
     * @return 加入请求列表
     */
    @GetMapping("/{groupId}/join-requests")
    public ResponseEntity<List<GroupJoinRequestResponse>> getGroupJoinRequests(@PathVariable String groupId) {
        // TODO: 实现获取群组加入请求逻辑
        return null;
    }
    
    /**
     * 处理群组加入请求
     * @param groupId 群组ID
     * @param requestId 请求ID
     * @param action 操作：accept或reject
     * @return 处理结果
     */
    @PostMapping("/{groupId}/join-requests/{requestId}")
    public ResponseEntity<Map<String, String>> handleJoinRequest(
            @PathVariable String groupId,
            @PathVariable String requestId,
            @RequestParam String action) {
        // TODO: 实现处理群组加入请求逻辑
        return null;
    }
} 