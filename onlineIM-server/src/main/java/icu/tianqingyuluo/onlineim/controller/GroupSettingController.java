//package icu.tianqingyuluo.onlineim.controller;
//
//import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupSettingUpdateRequest;
//import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupSettingResponse;
//import icu.tianqingyuluo.onlineim.service.GroupSettingService;
//import icu.tianqingyuluo.onlineim.service.UserService;
//import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
//import icu.tianqingyuluo.onlineim.util.JwtUtil;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
///**
// * 群组设置管理接口
// */
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/groups/{groupId}/settings")
//public class GroupSettingController {
//
//    private final JwtUtil jwtUtil;
//    private final UserService userService;
//    private final GroupSettingService groupSettingService;
//
//    public GroupSettingController(JwtUtil jwtUtil, UserService userService, GroupSettingService groupSettingService) {
//        this.jwtUtil = jwtUtil;
//        this.userService = userService;
//        this.groupSettingService = groupSettingService;
//    }
//
//    /**
//     * 获取群组设置
//     * @param groupId 群组ID
//     * @return 群组设置
//     */
//    @GetMapping("")
//    public ResponseEntity<?> getGroupSettings(
//            @PathVariable String groupId,
//            @RequestHeader("Authorization") String token) {
//        try {
//            String username = jwtUtil.getUsernameFromToken(token);
//            String userId = userService.getUserInfoByUsername(username).getUserId();
//
//            GroupSettingResponse response = groupSettingService.getGroupSettings(groupId, userId);
//            if (response == null) {
//                return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                        .body(ErrorCodeUtil.getErrorOutput("404", "未找到群组设置或无权限查看"));
//            }
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            log.error("获取群组设置失败: {}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 更新群组设置
//     * @param groupId 群组ID
//     * @param request 更新请求
//     * @return 更新结果
//     */
//    @PutMapping("")
//    public ResponseEntity<?> updateGroupSettings(
//            @PathVariable String groupId,
//            @RequestBody GroupSettingUpdateRequest request,
//            @RequestHeader("Authorization") String token) {
//        try {
//            String username = jwtUtil.getUsernameFromToken(token);
//            String userId = userService.getUserInfoByUsername(username).getUserId();
//
//            // 检查权限
//            // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, userId);
//            // if (!isAdminOrOwner) {
//            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
//            //             .body(ErrorCodeUtil.getErrorOutput("403", "无权限修改群组设置"));
//            // }
//
//            GroupSettingResponse response = groupSettingService.updateGroupSettings(groupId, request, userId);
//            if (response == null) {
//                return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                        .body(ErrorCodeUtil.getErrorOutput("404", "未找到群组设置或无权限修改"));
//            }
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            log.error("更新群组设置失败: {}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 启用全员禁言
//     * @param groupId 群组ID
//     * @return 操作结果
//     */
//    @PostMapping("/mute-all")
//    public ResponseEntity<?> muteAll(
//            @PathVariable String groupId,
//            @RequestHeader("Authorization") String token) {
//        try {
//            String username = jwtUtil.getUsernameFromToken(token);
//            String userId = userService.getUserInfoByUsername(username).getUserId();
//
//            // 检查权限
//            // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, userId);
//            // if (!isAdminOrOwner) {
//            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
//            //             .body(ErrorCodeUtil.getErrorOutput("403", "无权限启用全员禁言"));
//            // }
//
//            GroupSettingResponse response = groupSettingService.muteAll(groupId, userId);
//            if (response == null) {
//                return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                        .body(ErrorCodeUtil.getErrorOutput("404", "未找到群组或无权限操作"));
//            }
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            log.error("启用全员禁言失败: {}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//
//    /**
//     * 取消全员禁言
//     * @param groupId 群组ID
//     * @return 操作结果
//     */
//    @DeleteMapping("/mute-all")
//    public ResponseEntity<?> unmuteAll(
//            @PathVariable String groupId,
//            @RequestHeader("Authorization") String token) {
//        try {
//            String username = jwtUtil.getUsernameFromToken(token);
//            String userId = userService.getUserInfoByUsername(username).getUserId();
//
//            // 检查权限
//            // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, userId);
//            // if (!isAdminOrOwner) {
//            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
//            //             .body(ErrorCodeUtil.getErrorOutput("403", "无权限取消全员禁言"));
//            // }
//
//            GroupSettingResponse response = groupSettingService.unmuteAll(groupId, userId);
//            if (response == null) {
//                return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                        .body(ErrorCodeUtil.getErrorOutput("404", "未找到群组或无权限操作"));
//            }
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            log.error("取消全员禁言失败: {}", groupId, e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
//        }
//    }
//}