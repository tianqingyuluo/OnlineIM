package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupAnnouncementRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupAnnouncementResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 群组公告管理接口
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/announcements")
public class GroupAnnouncementController {

    /**
     * 获取群公告列表
     * @param groupId 群组ID
     * @return 公告列表
     */
    @GetMapping("")
    public ResponseEntity<List<GroupAnnouncementResponse>> getAnnouncements(@PathVariable String groupId) {
        // TODO: 实现获取群公告列表逻辑
        return null;
    }
    
    /**
     * 获取群公告详情
     * @param groupId 群组ID
     * @param announcementId 公告ID
     * @return 公告详情
     */
    @GetMapping("/{announcementId}")
    public ResponseEntity<GroupAnnouncementResponse> getAnnouncementDetail(
            @PathVariable String groupId,
            @PathVariable String announcementId) {
        // TODO: 实现获取群公告详情逻辑
        return null;
    }
    
    /**
     * 发布群公告
     * @param groupId 群组ID
     * @param request 公告请求
     * @return 发布结果
     */
    @PostMapping("")
    public ResponseEntity<GroupAnnouncementResponse> publishAnnouncement(
            @PathVariable String groupId,
            @RequestBody GroupAnnouncementRequest request) {
        // TODO: 实现发布群公告逻辑
        return null;
    }
    
    /**
     * 更新群公告
     * @param groupId 群组ID
     * @param announcementId 公告ID
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/{announcementId}")
    public ResponseEntity<GroupAnnouncementResponse> updateAnnouncement(
            @PathVariable String groupId,
            @PathVariable String announcementId,
            @RequestBody GroupAnnouncementRequest request) {
        // TODO: 实现更新群公告逻辑
        return null;
    }
    
    /**
     * 删除群公告
     * @param groupId 群组ID
     * @param announcementId 公告ID
     * @return 删除结果
     */
    @DeleteMapping("/{announcementId}")
    public ResponseEntity<Map<String, String>> deleteAnnouncement(
            @PathVariable String groupId,
            @PathVariable String announcementId) {
        // TODO: 实现删除群公告逻辑
        return null;
    }
    
    /**
     * 置顶/取消置顶群公告
     * @param groupId 群组ID
     * @param announcementId 公告ID
     * @param request 置顶请求
     * @return 置顶结果
     */
    @PutMapping("/{announcementId}/pin")
    public ResponseEntity<Map<String, String>> pinAnnouncement(
            @PathVariable String groupId,
            @PathVariable String announcementId,
            @RequestBody Map<String, Boolean> request) {
        // TODO: 实现置顶/取消置顶群公告逻辑
        return null;
    }
} 