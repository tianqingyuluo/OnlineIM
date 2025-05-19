//package icu.tianqingyuluo.onlineim.controller;
//
//import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupAnnouncementRequest;
//import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupAnnouncementResponse;
//import icu.tianqingyuluo.onlineim.service.GroupAnnouncementService;
//import icu.tianqingyuluo.onlineim.service.GroupMemberService;
//import icu.tianqingyuluo.onlineim.service.GroupService;
//import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
//import icu.tianqingyuluo.onlineim.util.JwtUtil;
//import org.apache.ibatis.exceptions.PersistenceException;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map;
//
///**
// * 群组公告管理接口
// */
//@RestController
//@RequestMapping("/api/v1/groups/{groupId}/announcements")
//public class GroupAnnouncementController {
//
//    private final GroupAnnouncementService groupAnnouncementService;
//    private final JwtUtil jwtUtil;
//    private final GroupService groupService;
//    private final GroupMemberService groupMemberService;
//
//    public GroupAnnouncementController(GroupAnnouncementService groupAnnouncementService, JwtUtil jwtUtil, GroupService groupService, GroupMemberService groupMemberService) {
//        this.groupAnnouncementService = groupAnnouncementService;
//        this.jwtUtil = jwtUtil;
//        this.groupService = groupService;
//        this.groupMemberService = groupMemberService;
//    }
//
//    /**
//     * 获取群公告列表
//     * @param groupId 群组ID
//     * @return 公告列表
//     */
//    @GetMapping("")
//    public ResponseEntity<?> getAnnouncements(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String groupId) {
//        // 设想 groupAnnouncementService.getAnnouncementsByGroupId(groupId) 方法:
//        // 功能: 根据群组ID获取该群组所有状态为正常的公告列表，并按创建时间降序排列，置顶公告优先。
//        // 返回: List<GroupAnnouncementResponse> 公告DTO列表
//        // 我又幻想了 groupMemberService.isGroupMember(groupId, userid) 方法:
//        // 功能: 检查该用户是否是群组成员
//        // 返回: bool
//        if (!groupMemberService.isGroupMember(groupId, jwtUtil.getUserIDFromToken(token))) {
//            ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "你没有该权限"));
//        }
//        List<GroupAnnouncementResponse> announcements = groupAnnouncementService.getAnnouncementsByGroupId(groupId);
//        if (announcements.isEmpty()) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "该群组还没有公告"));
//        }
//        return ResponseEntity.ok(announcements);
//    }
//
//    /**
//     * 获取群公告详情
//     * @param groupId 群组ID
//     * @param announcementId 公告ID
//     * @return 公告详情
//     */
//    @GetMapping("/{announcementId}")
//    public ResponseEntity<?> getAnnouncementDetail(
//            @PathVariable String groupId,
//            @PathVariable String announcementId) {
//        // 设想 groupAnnouncementService.getAnnouncementById(groupId, announcementId) 方法:
//        // 功能: 根据群组ID和公告ID获取特定公告的详细信息。
//        //      会校验公告是否属于该群组且状态正常。
//        // 返回: GroupAnnouncementResponse 公告DTO，如果找不到或不符合条件则可能返回null或抛出特定异常。
//        if (!groupMemberService.isGroupMember(groupId, jwtUtil.getUserIDFromToken(token))) {
//            ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "你没有该权限"));
//        }
//        try {
//            GroupAnnouncementResponse announcement = groupAnnouncementService.getAnnouncementById(groupId, announcementId);
//            if (announcement != null) {
//                return ResponseEntity.ok(announcement);
//            } else {
//                return ResponseEntity.notFound().build();
//            }
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//
//    }
//
//    /**
//     * 发布群公告
//     * @param groupId 群组ID
//     * @param request 公告请求
//     * @return 发布结果
//     */
//    @PostMapping("")
//    public ResponseEntity<?> publishAnnouncement(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String groupId,
//            @RequestBody GroupAnnouncementRequest request) {
//        String publisherId = jwtUtil.getUserIDFromToken(token); // 获取当前用户ID
//        // 设想 groupAnnouncementService.createAnnouncement(groupId, request, publisherId) 方法:
//        // 功能: 在指定群组中发布一条新的公告。
//        //      如果request中的pin为true，且群组内已有置顶公告，则可能需要处理置顶冲突（例如，取消旧的置顶）。
//        // 参数: groupId - 群组ID, request - 公告创建请求DTO, publisherId - 发布者用户ID
//        // 返回: GroupAnnouncementResponse 创建成功后的公告DTO
//        if (!groupService.isUserAdminOrOwner(groupId, jwtUtil.getUserIDFromToken(token))) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "你没有该权限"));
//        }
//        try {
//            GroupAnnouncementResponse newAnnouncement = groupAnnouncementService.createAnnouncement(groupId, request, publisherId);
//            return ResponseEntity.status(201).body(newAnnouncement); // 201 Created
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//
//    }
//
//    /**
//     * 更新群公告
//     * @param groupId 群组ID
//     * @param announcementId 公告ID
//     * @param request 更新请求
//     * @return 更新结果
//     */
//    @PutMapping("/{announcementId}")
//    public ResponseEntity<?> updateAnnouncement(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String groupId,
//            @PathVariable String announcementId,
//            @RequestBody GroupAnnouncementRequest request) {
//        String updaterId = jwtUtil.getUserIDFromToken(token); // 获取当前用户ID
//        // 设想 groupAnnouncementService.updateAnnouncement(groupId, announcementId, request, updaterId) 方法:
//        // 功能: 更新指定群组中特定公告的内容。
//        //      会校验公告是否属于该群组。
//        // 参数: groupId - 群组ID, announcementId - 公告ID, request - 公告更新请求DTO, updaterId - 操作者用户ID
//        // 返回: GroupAnnouncementResponse 更新成功后的公告DTO，如果找不到或无权限则可能返回null或抛出异常。
//        if (!groupService.isUserAdminOrOwner(groupId, updaterId)) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "你没有此权限"));
//        }
//        try {
//            GroupAnnouncementResponse updatedAnnouncement = groupAnnouncementService.updateAnnouncement(groupId, announcementId, request, updaterId);
//            if (updatedAnnouncement != null) {
//                return ResponseEntity.ok(updatedAnnouncement);
//            } else {
//                // 根据实际业务逻辑，这里可能是 notFound 或 forbidden
//                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeUtil.getErrorOutput("404", "更新错误，可能该公告已被删除"));
//            }
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//
//    }
//
//    /**
//     * 删除群公告
//     * @param groupId 群组ID
//     * @param announcementId 公告ID
//     * @return 删除结果
//     */
//    @DeleteMapping("/{announcementId}")
//    public ResponseEntity<Map<String, String>> deleteAnnouncement(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String groupId,
//            @PathVariable String announcementId) {
//        String deleterId = jwtUtil.getUserIDFromToken(token); // 获取当前用户ID
//        // 设想 groupAnnouncementService.deleteAnnouncement(groupId, announcementId, deleterId) 方法:
//        // 功能: 删除指定群组中的特定公告（通常是逻辑删除，如更新状态）。
//        //      会校验公告是否属于该群组。
//        // 参数: groupId - 群组ID, announcementId - 公告ID, deleterId - 操作者用户ID
//        // 返回: void (或 boolean 表示是否成功)
//        if (!groupService.isUserAdminOrOwner(groupId, deleterId)) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "你没有此权限"));
//        }
//        try {
//            groupAnnouncementService.deleteAnnouncement(groupId, announcementId, deleterId);
//            return ResponseEntity.ok().build();
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 置顶/取消置顶群公告
//     * @param groupId 群组ID
//     * @param announcementId 公告ID
//     * @param requestBody 置顶请求
//     * @return 置顶结果
//     */
//    @PutMapping("/{announcementId}/pin")
//    public ResponseEntity<Map<String, String>> pinAnnouncement(
//            @RequestHeader("Authorization") String token,
//            @PathVariable String groupId,
//            @PathVariable String announcementId,
//            @RequestBody Map<String, Boolean> requestBody) {
//        String operatorId = jwtUtil.getUserIDFromToken(token); // 获取当前用户ID
//        Boolean pinStatus = requestBody.get("pin");
//        if (pinStatus == null) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorCodeUtil.getErrorOutput("400", "必须提供置顶状态"));
//        }
//        // 设想 groupAnnouncementService.pinAnnouncement(groupId, announcementId, pinStatus, operatorId) 方法:
//        // 功能: 置顶或取消置顶指定群组中的特定公告。
//        //      会校验公告是否属于该群组。
//        //      如果设置为置顶，可能需要取消其他已置顶公告的置顶状态（根据业务规则，通常一个群只有一个置顶公告）。
//        // 参数: groupId - 群组ID, announcementId - 公告ID, pinStatus - true为置顶, false为取消置顶, operatorId - 操作者用户ID
//        // 返回: void (或 boolean 表示是否成功)
//        if (!groupService.isUserAdminOrOwner(groupId, operatorId)) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorCodeUtil.getErrorOutput("403", "你没有此权限"));
//        }
//        try {
//            groupAnnouncementService.pinAnnouncement(groupId, announcementId, pinStatus, operatorId);
//            return ResponseEntity.ok().build();
//        }
//        catch (PersistenceException e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//
//    }
//}