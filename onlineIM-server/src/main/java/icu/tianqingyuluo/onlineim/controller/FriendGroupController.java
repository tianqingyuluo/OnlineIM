package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendGroupResponse;
import icu.tianqingyuluo.onlineim.service.FriendGroupService;
import icu.tianqingyuluo.onlineim.service.impl.FriendGroupServiceImpl;
import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 好友分组管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/friend-groups")
public class FriendGroupController {

    private final JwtUtil jwtUtil;

    private final FriendGroupService friendGroupService;

    public FriendGroupController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        this.friendGroupService = new FriendGroupServiceImpl();
    }

    /**
     * 获取好友分组列表
     * @return 好友分组列表
     */
    @GetMapping("")
    public ResponseEntity<?> getFriendGroups(@RequestHeader("Authorization") String token) {
        String userId = jwtUtil.getUserIDFromToken(token);
        try {
            List<FriendGroupResponse> groups = friendGroupService.getFriendGroupsByUserId(userId);
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            log.error("获取好友分组列表失败: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }
    
    /**
     * 创建好友分组
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping("")
    public ResponseEntity<?> createFriendGroup(
            @RequestHeader("Authorization") String token,
            @RequestBody FriendGroupCreateRequest request) {
        String userId = jwtUtil.getUserIDFromToken(token);
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", "分组名称不能为空"));
        }
        
        try {
            FriendGroupResponse response = friendGroupService.createFriendGroup(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建好友分组失败: user={}, request={}", userId, request, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }
    
    /**
     * 更新好友分组
     * @param groupId 分组ID
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{groupId}")
    public ResponseEntity<?> updateFriendGroup(
            @RequestHeader("Authorization") String token,
            @PathVariable String groupId,
            @RequestBody FriendGroupUpdateRequest request) {
        String userId = jwtUtil.getUserIDFromToken(token);
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", "分组名称不能为空"));
        }
        
        try {
            FriendGroupResponse response = friendGroupService.updateFriendGroup(userId, groupId, request);
            if (response == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorCodeUtil.getErrorOutput("404", "分组未找到或更新失败"));
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新好友分组失败: user={}, group={}", userId, groupId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }
    
    /**
     * 删除好友分组
     * @param groupId 分组ID
     * @return 删除结果
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteFriendGroup(
            @RequestHeader("Authorization") String token,
            @PathVariable String groupId) {
        String userId = jwtUtil.getUserIDFromToken(token);
        
        try {
            boolean success = friendGroupService.deleteFriendGroup(userId, groupId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "分组删除成功"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", "删除分组失败，分组可能不存在或不为空"));
        } catch (Exception e) {
            log.error("删除好友分组失败: user={}, group={}", userId, groupId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }
    
    /**
     * 调整好友分组顺序
     * @param request 排序请求
     * @return 排序结果
     */
    @PutMapping("/sort")
    public ResponseEntity<?> sortFriendGroups(
            @RequestHeader("Authorization") String token,
            @RequestBody List<Map<String, Object>> request) {
        String userId = jwtUtil.getUserIDFromToken(token);
        
        if (request == null || request.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", "排序请求不能为空"));
        }
        
        try {
            boolean success = friendGroupService.sortFriendGroups(userId, request);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "分组排序成功"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", "分组排序失败"));
        } catch (Exception e) {
            log.error("调整好友分组顺序失败: user={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }
}