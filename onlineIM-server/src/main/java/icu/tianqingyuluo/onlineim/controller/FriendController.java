//package icu.tianqingyuluo.onlineim.controller;
//
//import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendRequestCreateRequest;
//import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendRequestResponse;
//import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendResponse;
//import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
//import icu.tianqingyuluo.onlineim.service.UserService;
//import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
//import icu.tianqingyuluo.onlineim.util.JwtUtil;
//import icu.tianqingyuluo.onlineim.util.enumeration.FriendshipStatusEnum;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.ibatis.exceptions.PersistenceException;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.*;
//
///**
// * 好友关系管理接口
// */
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/friends")
//public class FriendController {
//
//    private final JwtUtil jwtUtil;
//
//    private final FriendService friendService;
//
//    public FriendController(FriendService friendService, JwtUtil jwtUtil) {
//        this.friendService = friendService;
//        this.jwtUtil = jwtUtil;
//    }
//
//    /**
//     * 获取好友列表
//     * @return 好友列表
//     */
//    @GetMapping("")
//    public ResponseEntity<?> getFriends(@RequestHeader("Authorization") String token) {
//        // TODO: 实现获取好友列表逻辑
//        String username = jwtUtil.getUsernameFromToken(token);
//        List<FriendResponse> friends = friendService.fetchFriendsByUsername(username);
//
//        if (friends.isEmpty()) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404","啊哦，看起来你还没有好友"));
//        }
//
//        // 调整输出格式
//        Map<String,Object> response = new HashMap<>();
//        List<Map<String, Object>> friendList = new ArrayList<>();
//
//        friends.forEach(item->{
//            Map<String,Object> friend = new HashMap<>();
//            Map<String,Object> friendInfo = new HashMap<>();
//
//            friendInfo.put("user_id", item.getUserID());
//            friendInfo.put("username", item.getUsername());
//            friendInfo.put("nickname", item.getNickname());
//            friendInfo.put("avatar_url", item.getAvatarUrl());
//            friendInfo.put("remark",  item.getRemark());
//            friendInfo.put("friend_group_id", item.getFriendGroupID());
//
//            friend.put("friendship_id", item.getFriendshipID());
//            friend.put("friend_info", friendInfo);
//            friend.put("created_at", item.getCreatedAt());
//            friendList.add(friend);
//        });
//        response.put("friends",friendList);
//        response.put("total", friendList.size());
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * 获取好友详情
//     * @param friendId 好友ID
//     * @return 好友详情
//     */
//    @GetMapping("/{friendId}")
//    public ResponseEntity<?> getFriend(@PathVariable String friendId) {
//        // TODO: 实现获取好友详情逻辑
//        FriendResponse response = friendService.getByID(friendId);
//        if(response==null){
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "未找到该好友"));
//        }
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * 更新好友备注
//     * @param friendId 好友ID
//     * @param request 更新请求
//     * @return 更新结果
//     */
//    @PutMapping("/{friendId}/remark")
//    public ResponseEntity<Map<String, String>> updateRemark(@PathVariable String friendId, @RequestBody Map<String, String> request) {
//        // TODO: 实现更新好友备注逻辑
//        if (!friendService.existFriendByID(friendId)) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "未找到该好友"));
//        }
//        try {
//            friendService.updateRemarkByID(friendId, request.get("remark"));
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发生了些小错误"));
//        }
//
//        FriendResponse friend = friendService.getByID();
//        String remark = friend.getRemark();
//        Map<String, String> response = new HashMap<>();
//        response.put("friendship_id", friendId);
//        response.put("remark", remark);
//
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * 更新好友分组
//     * @param friendId 好友ID
//     * @param request 更新请求
//     * @return 更新结果
//     */
//    @PutMapping("/{friendId}/group")
//    public ResponseEntity<Map<String, String>> updateFriendGroup(@PathVariable String friendId, @RequestBody Map<String, String> request) {
//        // TODO: 实现更新好友分组逻辑
//        if (!friendService.existFriendByID(friendId)) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "未找到该好友"));
//        }
//        try {
//            friendService.updateGroupByID(friendId, request.get("friend_group_id"));
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发生了些小错误"));
//        }
//
//        FriendResponse friend = friendService.getByID(friendId);
//        Map<String,String> response = new HashMap<>();
//        response.put("friendship_id", friendId);
//        response.put("friendship_group_id", friend.getFriendGroupID());
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * 拉黑好友
//     * @param friendId 好友ID
//     * @return 操作结果
//     */
//    @PutMapping("/{friendId}/block")
//    public ResponseEntity<Map<String, String>> blockFriend(@PathVariable String friendId) {
//        // TODO: 实现拉黑好友逻辑
//        if (!friendService.existFriendByID(friendId)) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "未找到该好友"));
//        }
//        try {
//            friendService.updateStatusByID(friendId, FriendshipStatusEnum.BLOCKED.getValue());
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发生了些小错误"));
//        }
//        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//    }
//
//    /**
//     * 取消拉黑好友
//     * @param friendId 好友ID
//     * @return 操作结果
//     */
//    @DeleteMapping("/{friendId}/block")
//    public ResponseEntity<Map<String, String>> unblockFriend(@PathVariable String friendId) {
//        // TODO: 实现取消拉黑好友逻辑
//        if (!friendService.existFriendByID(friendId)) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "未找到该好友"));
//        }
//        try {
//            friendService.updateStatusByID(friendId, FriendshipStatusEnum.NORMAL.getValue());
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发送了些小错误"));
//            log.error(e.getMessage());
//        }
//        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//    }
//
//    /**
//     * 获取黑名单列表
//     * @return 黑名单列表
//     */
//    @GetMapping("/blacklist")
//    public ResponseEntity<?> getBlacklist(@RequestHeader("Authorization") String token) {
//        // TODO: 实现获取黑名单列表逻辑
//        String username = jwtUtil.getUsernameFromToken(token);
//        try {
//            List<UserBriefResponse> userBriefResponses = friendService.getBlackListByUsername(username);
//            Map<String,Object> response = new HashMap<>();
//            response.put("blasklist", userBriefResponses);
//            response.put("total", userBriefResponses.size());
//            return ResponseEntity.status(HttpStatus.OK).body(response);
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发送了些小错误"));
//        }
//        if (userBriefResponses.isEmpty()) {
//            return ResponseEntity.status(404).body(ErrorCodeUtil.getErrorOutput("404", "看起来你还没有把任一好友拉入黑名单"));
//        }
//    }
//
//    /**
//     * 发送好友请求
//     * @param request 好友请求
//     * @return 请求结果
//     */
//    @PostMapping("/requests")
//    public ResponseEntity<Map<String, String>> sendFriendRequest(@RequestBody FriendRequestCreateRequest request) {
//        // TODO: 实现发送好友请求逻辑
//        return null;
//    }
//
//    /**
//     * 获取收到的好友请求
//     * @return 好友请求列表
//     */
//    @GetMapping("/requests/received")
//    public ResponseEntity<List<FriendRequestResponse>> getReceivedFriendRequests() {
//        // TODO: 实现获取收到的好友请求逻辑
//        return null;
//    }
//
//    /**
//     * 获取发送的好友请求
//     * @return 好友请求列表
//     */
//    @GetMapping("/requests/sent")
//    public ResponseEntity<List<FriendRequestResponse>> getSentFriendRequests() {
//        // TODO: 实现获取发送的好友请求逻辑
//        return null;
//    }
//
//    /**
//     * 接受好友请求
//     * @param requestId 请求ID
//     * @return 操作结果
//     */
//    @PostMapping("/requests/{requestId}/accept")
//    public ResponseEntity<Map<String, String>> acceptFriendRequest(@PathVariable String requestId) {
//        // TODO: 实现接受好友请求逻辑
//        return null;
//    }
//
//    /**
//     * 拒绝好友请求
//     * @param requestId 请求ID
//     * @return 操作结果
//     */
//    @PostMapping("/requests/{requestId}/reject")
//    public ResponseEntity<Map<String, String>> rejectFriendRequest(@PathVariable String requestId) {
//        // TODO: 实现拒绝好友请求逻辑
//        return null;
//    }
//}