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
}
