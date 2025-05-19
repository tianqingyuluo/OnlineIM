//package icu.tianqingyuluo.onlineim.controller;
//
//import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
//import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupCreateRequest;
//import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupJoinRequest;
//import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupUpdateRequest;
//import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupJoinRequestResponse;
//import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
//import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupResponse;
//import icu.tianqingyuluo.onlineim.service.GroupService;
//import icu.tianqingyuluo.onlineim.service.UserService;
//import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
//import icu.tianqingyuluo.onlineim.util.JwtUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.ibatis.exceptions.PersistenceException;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.util.List;
//import java.util.Map;
//
///**
// * 群组管理接口
// */
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/groups")
//public class GroupController {
//
//    private final JwtUtil jwtUtil;
//    private final UserService userService;
//    private final GroupService groupService;
//
//    public GroupController(JwtUtil jwtUtil, UserService userService, GroupService groupService) {
//        this.jwtUtil = jwtUtil;
//        this.userService = userService;
//        this.groupService = groupService;
//    }
//
//    /**
//     * 创建群组
//     * @param request 创建请求
//     * @return 创建结果
//     */
//    @PostMapping("")
//    public ResponseEntity<?> createGroup(@RequestBody GroupCreateRequest request, @RequestHeader("Authorization") String token) {
//        // TODO: 实现创建群组逻辑
//        String username = jwtUtil.getUsernameFromToken(token);
//        String userID = userService.getUserInfoByUsername(username).getUserId();
//
//        if (request.getMaxMembers() > 2000) {
//            // 设置最大人数不大于500
//            request.setMaxMembers(2000);
//        }
//        try {
//            String groupID = groupService.createGroup(request, userID);
//            return ResponseEntity.ok(groupService.getGroupbyID(groupID));
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 获取群组详情
//     * @param groupId 群组ID
//     * @return 群组详情
//     */
//    @GetMapping("/{groupId}")
//    public ResponseEntity<?> getGroupDetail(@PathVariable String groupId) {
//        // TODO: 实现获取群组详情逻辑
//        try {
//            GroupResponse response = groupService.getGroupDetailByID(groupId);
//            if (response == null) {
//                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "没有找到该群聊"));
//            }
//            return ResponseEntity.ok(response);
//        }
//        catch (PersistenceException e) {
//            log.error(e.getMessage());
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "啊哦，发生了些小错误"));
//        }
//    }
//
//    /**
//     * 更新群组信息
//     * @param groupId 群组ID
//     * @param request 更新请求
//     * @return 更新结果
//     */
//    @PutMapping("/{groupId}")
//    public ResponseEntity<?> updateGroup(@RequestHeader("Authorization") String token, @PathVariable String groupId, @RequestBody GroupUpdateRequest request) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String userID = userService.getUserInfoByUsername(username).getUserId();
//        // 检查用户是否有权限更新群组信息 (例如, 是否为群主或管理员)
//        // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, userID);
//        // if (!isAdminOrOwner) {
//        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "无权限操作"));
//        // }
//        try {
//            // GroupService.updateGroupByID(String groupId, GroupUpdateRequest request, String operatorId)
//            // 该方法应负责更新群组信息，并返回更新后的群组信息 GroupResponse。
//            // operatorId 用于权限校验和记录操作日志。
//            GroupResponse response = groupService.updateGroupByID(groupId, request, userID);
//            if (response == null) {
//                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "群组未找到或更新失败"));
//            }
//            return ResponseEntity.ok(response);
//        }
//        catch (PersistenceException e) {
//            log.error("更新群组信息失败: {}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 解散群组
//     * @param groupId 群组ID
//     * @return 解散结果
//     */
//    @DeleteMapping("/{groupId}")
//    public ResponseEntity<?> disbandGroup(@RequestHeader("Authorization") String token, @PathVariable String groupId) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String userID = userService.getUserInfoByUsername(username).getUserId();
//        try {
//            // GroupService.disbandGroupByID(String groupId, String operatorId)
//            // 该方法应负责校验操作者是否为群主，然后解散群组（例如更新群组状态为已解散）。
//            // 成功则返回成功信息，失败则抛出异常或返回错误信息。
//            boolean success = groupService.disbandGroupByID(groupId, userID);
//            if (success) {
//                return ResponseEntity.ok(Map.of("message", "群组已成功解散"));
//            }
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "解散群组失败，可能因为您不是群主或群组不存在"));
//        }
//        catch (PersistenceException e) {
//            log.error("解散群组失败: {}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 获取用户加入的群组列表
//     * @return 群组列表
//     */
//    @GetMapping("/joined")
//    public ResponseEntity<?> getJoinedGroups(@RequestHeader("Authorization") String token) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String userID = userService.getUserInfoByUsername(username).getUserId();
//        try {
//            // GroupService.getJoinedGroupsByUserID(String userId)
//            // 该方法应负责查询指定用户加入的所有群组列表，并返回 List<GroupResponse>。
//            List<GroupResponse> groups = groupService.getJoinedGroupsByUserID(userID);
//            return ResponseEntity.ok(groups);
//        }
//        catch (PersistenceException e) {
//            log.error("获取用户加入的群组列表失败: {}", userID, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 上传群头像
//     * @param groupId 群组ID
//     * @param avatar 头像文件
//     * @return 上传结果
//     */
//    @PostMapping("/{groupId}/avatar")
//    public ResponseEntity<?> uploadGroupAvatar(@RequestHeader("Authorization") String token, @PathVariable String groupId, @RequestParam("file") MultipartFile avatar) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String userID = userService.getUserInfoByUsername(username).getUserId();
//        // 检查用户是否有权限修改群头像 (例如, 是否为群主或管理员)
//         boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, userID);
//         if (!isAdminOrOwner) {
//             return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "无权限操作"));
//         }
//        try {
//            // 该方法应负责处理群头像上传（保存文件，更新数据库记录），并返回头像的URL或成功信息。
//            String avatarUrl = groupService.uploadGroupAvatar(groupId, avatar, userID);
//            if (avatarUrl != null && !avatarUrl.isEmpty()) {
//                return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
//            }
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "上传群头像失败"));
//        }
//        catch (Exception e) {
//            log.error("上传群头像失败: {}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 获取群成员列表
//     * @param groupId 群组ID
//     * @return 成员列表
//     */
//    @GetMapping("/{groupId}/members")
//    public ResponseEntity<?> getGroupMembers(@PathVariable String groupId, @RequestHeader("Authorization") String token) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String userID = userService.getUserInfoByUsername(username).getUserId();
//        // 检查用户是否是群成员，有权限查看成员列表
//        // boolean isMember = groupService.isUserMember(groupId, userID);
//        // if (!isMember) {
//        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "您不是该群成员，无权限查看"));
//        // }
//        try {
//            // GroupService.getGroupMembersByGroupID(String groupId)
//            // 该方法应负责查询指定群组的所有成员列表，并返回 List<GroupMemberResponse>。
//            List<GroupMemberResponse> members = groupService.getGroupMembersByGroupID(groupId);
//            return ResponseEntity.ok(members);
//        }
//        catch (PersistenceException e) {
//            log.error("获取群成员列表失败: {}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 邀请用户加入群组
//     * @param groupId 群组ID
//     * @param request 邀请请求，包含被邀请用户ID列表: {"user_ids": ["usr_id1", "usr_id2"]}
//     * @return 邀请结果
//     */
//    @PostMapping("/{groupId}/invite")
//    public ResponseEntity<?> inviteToGroup(@RequestHeader("Authorization") String token, @PathVariable String groupId, @RequestBody Map<String, List<String>> request) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String inviterId = userService.getUserInfoByUsername(username).getUserId();
//        List<String> userIdsToInvite = request.get("user_ids");
//
//        if (userIdsToInvite == null || userIdsToInvite.isEmpty()) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "被邀请用户列表不能为空"));
//        }
//
//        // 检查邀请人是否有权限邀请 (例如, 是否为群成员，且群设置允许成员邀请，或者邀请人是管理员/群主)
//        // boolean canInvite = groupService.canUserInvite(groupId, inviterId);
//        // if (!canInvite) {
//        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "无权限邀请用户"));
//        // }
//
//        try {
//            // GroupService.inviteUsersToGroup(String groupId, List<String> userIdsToInvite, String inviterId)
//            // 该方法应负责处理邀请逻辑：
//            // 1. 校验邀请人权限。
//            // 2. 校验群组是否存在，是否允许邀请。
//            // 3. 校验被邀请用户是否存在，是否已经是成员，是否已在请求列表。
//            // 4. 根据群组的 join_type，可能直接添加成员()或创建邀请记录/入群申请。
//            // 返回成功或部分成功的信息，或错误信息。
//            boolean success = groupService.inviteUsersToGroup(groupId, userIdsToInvite, inviterId);
//            if (success) {
//                return ResponseEntity.ok(Map.of("message", "邀请已发送"));
//            }
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "邀请失败，请检查群组设置或被邀请用户信息"));
//        }
//        catch (Exception e) {
//            log.error("邀请用户加入群组失败: group={}, inviter={}, users={}", groupId, inviterId, userIdsToInvite, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 申请加入群组
//     * @param groupId 群组ID
//     * @param request 申请请求
//     * @return 申请结果
//     */
//    @PostMapping("/{groupId}/join")
//    public ResponseEntity<?> joinGroup(@RequestHeader("Authorization") String token, @PathVariable String groupId, @RequestBody(required = false) GroupJoinRequest request) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String applicantId = userService.getUserInfoByUsername(username).getUserId();
//        String message = (request != null) ? request.getMessage() : null;
//
//        try {
//            // GroupService.requestToJoinGroup(String groupId, String applicantId, String message)
//            // 该方法应负责处理用户加群申请：
//            // 1. 校验群组是否存在，加群方式（join_type）。
//            // 2. 校验用户是否已是成员或已在申请中。
//            // 3. 如果无需验证，直接添加为成员,该方法应该直接返回一个group_members_id,但是也需要在group_join_requests表中留下记录,status直接填1。
//            // 4. 如果需要验证，创建加群请求记录并且返回一个group_request。
//            // 5. 如果禁止加入，返回ForbiddenException错误。
//            // 返回成功信息或错误信息。
//            String responseMessage = groupService.requestToJoinGroup(groupId, applicantId, message);
//            return ResponseEntity.ok(Map.of("message", responseMessage));
//        }
//        catch (ForbiddenException e) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "该群只允许邀请加入"));
//        }
//        catch (IllegalArgumentException e) {
//            log.warn("申请加入群组参数错误: group={}, applicant={}, message={}", groupId, applicantId, message, e);
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "申请失败，请再试一遍"));
//        }
//        catch (Exception e) {
//            log.error("申请加入群组失败: group={}, applicant={}", groupId, applicantId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 退出群组
//     * @param groupId 群组ID
//     * @return 退出结果
//     */
//    @PostMapping("/{groupId}/quit")
//    public ResponseEntity<?> quitGroup(@RequestHeader("Authorization") String token, @PathVariable String groupId) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String userId = userService.getUserInfoByUsername(username).getUserId();
//
//        try {
//            // GroupService.quitGroup(String groupId, String userId)
//            // 该方法应负责处理用户退群：
//            // 1. 校验用户是否是群成员。
//            // 2. 如果是群主，需要先转让群主或解散群组（根据业务逻辑）。
//            // 3. 更新成员状态或删除成员记录。
//            // 返回成功信息或错误信息。
//            boolean success = groupService.quitGroup(groupId, userId);
//            if (success) {
//                return ResponseEntity.ok(Map.of("message", "已成功退出群组"));
//            }
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "退出群组失败，您可能不是群成员或操作不允许"));
//        }
//        catch (IllegalStateException e) { // 例如群主试图直接退出
//             log.warn("退出群组操作不允许: group={}, user={}", groupId, userId, e);
//             return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
//        }
//        catch (Exception e) {
//            log.error("退出群组失败: group={}, user={}", groupId, userId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 搜索群组
//     * @param keyword 关键词 (可以按群名或群ID搜索)
//     * @return 群组列表
//     */
//    @GetMapping("/search")
//    public ResponseEntity<?> searchGroups(@RequestParam String keyword) {
//        if (keyword == null || keyword.trim().isEmpty()) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "搜索关键词不能为空"));
//        }
//        try {
//            // GroupService.searchGroups(String keyword)
//            // 该方法应负责根据关键词搜索群组（如按群名、群ID模糊匹配），并返回 List<GroupResponse>。
//            List<GroupResponse> groups = groupService.searchGroups(keyword.trim());
//            return ResponseEntity.ok(groups);
//        }
//        catch (Exception e) {
//            log.error("搜索群组失败: keyword={}", keyword, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 获取群组加入请求 (通常由群主或管理员查看)
//     * @param groupId 群组ID
//     * @return 加入请求列表
//     */
//    @GetMapping("/{groupId}/join-requests")
//    public ResponseEntity<?> getGroupJoinRequests(@RequestHeader("Authorization") String token, @PathVariable String groupId) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String operatorId = userService.getUserInfoByUsername(username).getUserId();
//
//        // 检查操作者是否有权限查看加入请求 (例如, 是否为群主或管理员)
//        // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, operatorId);
//        // if (!isAdminOrOwner) {
//        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "无权限查看加群请求"));
//        // }
//
//        try {
//            // GroupService.getGroupJoinRequests(String groupId, String operatorId)
//            // 该方法应负责查询指定群组的待处理加入请求列表，并返回 List<GroupJoinRequestResponse>。
//            // operatorId 用于权限校验。
//            List<GroupJoinRequestResponse> requests = groupService.getGroupJoinRequests(groupId, operatorId);
//            return ResponseEntity.ok(requests);
//        }
//        catch (Exception e) {
//            log.error("获取群组加入请求失败: group={}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 处理群组加入请求 (通常由群主或管理员操作)
//     * @param groupId 群组ID
//     * @param requestId 请求ID
//     * @param action 操作："accept" 或 "reject"
//     * @return 处理结果
//     */
//    @PostMapping("/{groupId}/join-requests/{requestId}")
//    public ResponseEntity<?> handleJoinRequest(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String groupId,
//            @PathVariable String requestId,
//            @RequestParam String action) {
//        String username = jwtUtil.getUsernameFromToken(token);
//        String operatorId = userService.getUserInfoByUsername(username).getUserId();
//
//        if (!"accept".equalsIgnoreCase(action) && !"reject".equalsIgnoreCase(action)) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "无效的操作参数，请使用 'accept' 或 'reject'"));
//        }
//
//        // 检查操作者是否有权限处理加入请求 (例如, 是否为群主或管理员)
//        // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, operatorId);
//        // if (!isAdminOrOwner) {
//        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "无权限处理加群请求"));
//        // }
//
//        try {
//            // GroupService.handleJoinRequest(String groupId, String requestId, String action, String operatorId)
//            // 该方法应负责处理加群请求：
//            // 1. 校验操作者权限。
//            // 2. 校验请求是否存在且状态为待处理。
//            // 3. 如果是 'accept'，则将申请人添加为群成员，并更新请求状态。
//            // 4. 如果是 'reject'，则更新请求状态。
//            // 返回成功信息或错误信息。
//            boolean success = groupService.handleJoinRequest(groupId, requestId, action, operatorId);
//            if (success) {
//                return ResponseEntity.ok(Map.of("message", "请求处理成功"));
//            }
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "处理请求失败，请检查请求是否存在或已被处理"));
//        }
//        catch (IllegalArgumentException e) {
//            log.warn("处理群组加入请求参数错误: group={}, request={}, action={}", groupId, requestId, action, e);
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
//        }
//        catch (Exception e) {
//            log.error("处理群组加入请求失败: group={}, request={}, action={}", groupId, requestId, action, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//}