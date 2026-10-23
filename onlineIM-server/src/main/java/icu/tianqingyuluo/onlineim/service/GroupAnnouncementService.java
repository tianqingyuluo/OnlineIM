package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupAnnouncementRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupAnnouncementResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface GroupAnnouncementService {

    List<GroupAnnouncementResponse> getAnnouncementsByGroupId(String groupId);

    GroupAnnouncementResponse getAnnouncementById(String groupId, String announcementId);

    GroupAnnouncementResponse createAnnouncement(String groupId, GroupAnnouncementRequest request, String publisherId);

    GroupAnnouncementResponse updateAnnouncement(String groupId, String announcementId, GroupAnnouncementRequest request, String updaterId);

    void deleteAnnouncement(String groupId, String announcementId, String deleterId);

    void pinAnnouncement(String groupId, String announcementId, Boolean pinStatus, String operatorId);

    /** 变更置顶前先记下当前置顶，用来给被取消的那几条补推帧 */
    List<String> listPinnedIds(String groupId);
}
