package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendGroupResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友分组管理接口
 */
@RestController
@RequestMapping("/api/v1/friend-groups")
public class FriendGroupController {

    /**
     * 获取好友分组列表
     * @return 好友分组列表
     */
    @GetMapping("")
    public ResponseEntity<List<FriendGroupResponse>> getFriendGroups() {
        // TODO: 实现获取好友分组列表逻辑
        return null;
    }
    
    /**
     * 创建好友分组
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping("")
    public ResponseEntity<FriendGroupResponse> createFriendGroup(@RequestBody FriendGroupCreateRequest request) {
        // TODO: 实现创建好友分组逻辑
        return null;
    }
    
    /**
     * 更新好友分组
     * @param groupId 分组ID
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{groupId}")
    public ResponseEntity<FriendGroupResponse> updateFriendGroup(@PathVariable String groupId, @RequestBody FriendGroupUpdateRequest request) {
        // TODO: 实现更新好友分组逻辑
        return null;
    }
    
    /**
     * 删除好友分组
     * @param groupId 分组ID
     * @return 删除结果
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Map<String, String>> deleteFriendGroup(@PathVariable String groupId) {
        // TODO: 实现删除好友分组逻辑
        return null;
    }
    
    /**
     * 调整好友分组顺序
     * @param request 排序请求
     * @return 排序结果
     */
    @PutMapping("/sort")
    public ResponseEntity<Map<String, String>> sortFriendGroups(@RequestBody List<Map<String, Object>> request) {
        // TODO: 实现调整好友分组顺序逻辑
        return null;
    }
} 