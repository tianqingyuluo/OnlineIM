package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.UserService;
import icu.tianqingyuluo.onlineim.util.ErrorCodeUtil;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 群组成员管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/groups/{groupId}/members")
public class GroupMemberController {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final GroupMemberService groupMemberService;

    public GroupMemberController(JwtUtil jwtUtil, UserService userService, GroupMemberService groupMemberService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.groupMemberService = groupMemberService;
    }

    /**
     * 获取群成员详情
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @return 成员详情
     */
    @GetMapping("/{memberId}")
    public ResponseEntity<?> getMemberDetail(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            String operatorId = userService.getUserInfoByUsername(username).getUserId();

            GroupMemberResponse response = groupMemberService.getMemberDetail(groupId, memberId, operatorId);
            if (response == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorCodeUtil.getErrorOutput("404", "未找到该群成员或无权限查看"));
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取群成员详情失败: group={}, member={}", groupId, memberId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }

    /**
     * 移除群成员
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @return 移除结果
     */
    @DeleteMapping("/{memberId}")
    public ResponseEntity<?> removeMember(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            String operatorId = userService.getUserInfoByUsername(username).getUserId();

            // 检查权限
            // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, operatorId);
            // if (!isAdminOrOwner) {
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
            //             .body(ErrorCodeUtil.getErrorOutput("403", "无权限移除群成员"));
            // }

            boolean success = groupMemberService.removeMember(groupId, memberId, operatorId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "成员已成功移除"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "移除失败，可能是权限不足或成员不存在"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
        } catch (Exception e) {
            log.error("移除群成员失败: group={}, member={}", groupId, memberId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }

    /**
     * 设置群管理员
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @return 设置结果
     */
    @PostMapping("/{memberId}/set-admin")
    public ResponseEntity<?> setAdmin(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            String operatorId = userService.getUserInfoByUsername(username).getUserId();

            // 检查权限，只有群主可以设置管理员
            // boolean isOwner = groupService.isUserOwner(groupId, operatorId);
            // if (!isOwner) {
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
            //             .body(ErrorCodeUtil.getErrorOutput("403", "只有群主可以设置管理员"));
            // }

            boolean success = groupMemberService.setAdmin(groupId, memberId, operatorId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "已成功设置为管理员"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "设置失败，可能是权限不足或成员不存在"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
        } catch (Exception e) {
            log.error("设置群管理员失败: group={}, member={}", groupId, memberId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }

    /**
     * 取消群管理员
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @return 取消结果
     */
    @PostMapping("/{memberId}/cancel-admin")
    public ResponseEntity<?> cancelAdmin(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            String operatorId = userService.getUserInfoByUsername(username).getUserId();

            // 检查权限，只有群主可以取消管理员
            // boolean isOwner = groupService.isUserOwner(groupId, operatorId);
            // if (!isOwner) {
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
            //             .body(ErrorCodeUtil.getErrorOutput("403", "只有群主可以取消管理员"));
            // }

            boolean success = groupMemberService.cancelAdmin(groupId, memberId, operatorId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "已成功取消管理员身份"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "操作失败，可能是权限不足或成员不存在"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
        } catch (Exception e) {
            log.error("取消群管理员失败: group={}, member={}", groupId, memberId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }

    /**
     * 更新群成员昵称
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param request 更新请求 {"nickname": "新昵称"}
     * @return 更新结果
     */
    @PutMapping("/{memberId}/nickname")
    public ResponseEntity<?> updateMemberNickname(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            String operatorId = userService.getUserInfoByUsername(username).getUserId();
            String nickname = request.get("nickname");

            if (nickname == null || nickname.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "昵称不能为空"));
            }

            // 检查权限，成员只能修改自己的昵称，管理员和群主可以修改任何人的昵称
            // boolean isSelfOrAdminOrOwner = memberId.equals(operatorId) ||
            //         groupService.isUserAdminOrOwner(groupId, operatorId);
            // if (!isSelfOrAdminOrOwner) {
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
            //             .body(ErrorCodeUtil.getErrorOutput("403", "无权修改他人昵称"));
            // }

            boolean success = groupMemberService.updateMemberNickname(groupId, memberId, nickname.trim(), operatorId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "昵称更新成功"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "昵称更新失败，可能是权限不足或成员不存在"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
        } catch (Exception e) {
            log.error("更新群成员昵称失败: group={}, member={}", groupId, memberId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }

    /**
     * 禁言群成员
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @param request 禁言请求 {"duration": 30} 单位：分钟
     * @return 禁言结果
     */
    @PostMapping("/{memberId}/mute")
    public ResponseEntity<?> muteMember(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestBody Map<String, Object> request,
            @RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            String operatorId = userService.getUserInfoByUsername(username).getUserId();

            Integer duration = null;
            if (request.containsKey("duration")) {
                try {
                    duration = Integer.parseInt(request.get("duration").toString());
                } catch (NumberFormatException e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ErrorCodeUtil.getErrorOutput("400", "禁言时长格式不正确"));
                }
            }

            if (duration == null || duration <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "禁言时长必须大于0"));
            }

            // 检查权限
            // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, operatorId);
            // if (!isAdminOrOwner) {
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
            //             .body(ErrorCodeUtil.getErrorOutput("403", "无权限禁言群成员"));
            // }

            boolean success = groupMemberService.muteMember(groupId, memberId, duration, operatorId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "成员已被禁言"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "禁言失败，可能是权限不足或成员不存在"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
        } catch (Exception e) {
            log.error("禁言群成员失败: group={}, member={}", groupId, memberId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }

    /**
     * 取消群成员禁言
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @return 取消结果
     */
    @PostMapping("/{memberId}/unmute")
    public ResponseEntity<?> unmuteMember(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            String operatorId = userService.getUserInfoByUsername(username).getUserId();

            // 检查权限
            // boolean isAdminOrOwner = groupService.isUserAdminOrOwner(groupId, operatorId);
            // if (!isAdminOrOwner) {
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
            //             .body(ErrorCodeUtil.getErrorOutput("403", "无权限取消禁言"));
            // }

            boolean success = groupMemberService.unmuteMember(groupId, memberId, operatorId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "已取消成员禁言"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "取消禁言失败，可能是权限不足或成员不存在"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
        } catch (Exception e) {
            log.error("取消群成员禁言失败: group={}, member={}", groupId, memberId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }

    /**
     * 转让群主
     * @param groupId 群组ID
     * @param memberId 成员ID
     * @return 转让结果
     */
    @PostMapping("/{memberId}/transfer-ownership")
    public ResponseEntity<?> transferOwnership(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            String operatorId = userService.getUserInfoByUsername(username).getUserId();

            // 检查权限，只有群主才能转让群主身份
            // boolean isOwner = groupService.isUserOwner(groupId, operatorId);
            // if (!isOwner) {
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
            //             .body(ErrorCodeUtil.getErrorOutput("403", "只有群主可以转让群主身份"));
            // }

            boolean success = groupMemberService.transferOwnership(groupId, memberId, operatorId);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "群主身份已成功转让"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorCodeUtil.getErrorOutput("400", "转让失败，可能是成员不存在或已是群主"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorCodeUtil.getErrorOutput("400", e.getMessage()));
        } catch (Exception e) {
            log.error("转让群主失败: group={}, newOwner={}", groupId, memberId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorCodeUtil.getErrorOutput("500", "服务器内部错误"));
        }
    }
}