package icu.tianqingyuluo.onlineim.service;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.mapper.GroupAnnouncementReadMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.AnnouncementReadCountRow;
import icu.tianqingyuluo.onlineim.pojo.dto.response.AnnouncementReaderResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupAnnouncementReadListResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupAnnouncementResponse;
import icu.tianqingyuluo.onlineim.util.AfterCommit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 群公告已读：成员确认、群主/管理员看名单，并通过 Redis Stream 通知在线成员。
 */
@Slf4j
@Service
public class GroupAnnouncementReadService {

    public static final String STREAM_ANNOUNCEMENT = "GROUP_ANNOUNCEMENT";
    public static final String STREAM_READ = "GROUP_ANNOUNCEMENT_READ";

    private final GroupAnnouncementReadMapper readMapper;
    private final GroupMemberService groupMemberService;
    private final RedisStreamService redisStreamService;
    private final ObjectMapper objectMapper;

    public GroupAnnouncementReadService(GroupAnnouncementReadMapper readMapper,
                                        GroupMemberService groupMemberService,
                                        RedisStreamService redisStreamService,
                                        ObjectMapper objectMapper) {
        this.readMapper = readMapper;
        this.groupMemberService = groupMemberService;
        this.redisStreamService = redisStreamService;
        this.objectMapper = objectMapper;
    }

    public void attach(String groupId, String viewerId, List<GroupAnnouncementResponse> announcements) {
        if (announcements == null || announcements.isEmpty() || viewerId == null) {
            return;
        }
        backfillPublishersWithoutReads(groupId, announcements);
        List<String> readIds = readMapper.listReadAnnouncementIds(groupId, viewerId);
        java.util.Set<String> read = new LinkedHashSet<>(readIds == null ? List.of() : readIds);
        boolean manager = groupMemberService.isAdminOrOwner(groupId, viewerId);
        Map<String, Integer> counts = manager ? readCounts(groupId) : Map.of();
        int members = manager ? readMapper.countActiveMembers(groupId) : 0;
        for (GroupAnnouncementResponse announcement : announcements) {
            if (announcement == null) {
                continue;
            }
            announcement.setReadByMe(read.contains(announcement.getAnnouncementID()));
            if (manager) {
                int readCount = counts.getOrDefault(announcement.getAnnouncementID(), 0);
                announcement.setReadCount(readCount);
                announcement.setUnreadCount(Math.max(0, members - readCount));
            }
        }
    }

    public GroupAnnouncementReadListResponse listReads(String groupId, String announcementId, String viewerId) {
        if (!groupMemberService.isGroupMember(groupId, viewerId)) {
            throw new ForbiddenException("你没有该权限");
        }
        if (!groupMemberService.isAdminOrOwner(groupId, viewerId)) {
            throw new ForbiddenException("只有群主或管理员可以查看已读名单");
        }
        List<AnnouncementReaderResponse> readers = readMapper.listReaders(announcementId);
        List<AnnouncementReaderResponse> unread = readMapper.listUnreadMembers(groupId, announcementId);
        if (readers == null) {
            readers = List.of();
        }
        if (unread == null) {
            unread = List.of();
        }
        return GroupAnnouncementReadListResponse.builder()
                .announcementId(announcementId)
                .groupId(groupId)
                .readCount(readers.size())
                .unreadCount(unread.size())
                .readers(readers)
                .unreadUsers(unread)
                .build();
    }

    /**
     * 成员确认已读。重复确认保持第一次的时间，也不再推送。
     */
    @Transactional
    public boolean markRead(String groupId, String announcementId, String userId) {
        return markRead(groupId, announcementId, userId, null);
    }

    /**
     * 打开/确认公告。还没有任何已读行时（功能上线前的旧公告，或已读被清空），
     * 先把发布者补成已读，再记下当前查看的人，这样「已读」不只有新公告才有记录。
     */
    @Transactional
    public boolean markRead(String groupId, String announcementId, String userId, String publisherId) {
        if (!groupMemberService.isGroupMember(groupId, userId)) {
            throw new ForbiddenException("你没有该权限");
        }
        backfillPublisherIfUntracked(groupId, announcementId, userId, publisherId);
        if (readMapper.countByUser(announcementId, userId) > 0) {
            return false;
        }
        try {
            readMapper.insert("anr_" + IdUtil.getSnowflakeNextIdStr(), announcementId, groupId, userId);
        } catch (DuplicateKeyException ignored) {
            return false;
        }
        AfterCommit.run(() -> publishRead(groupId, announcementId, userId));
        return true;
    }

    /** 发布成功：操作者自己算已读，并通知其他成员。 */
    @Transactional
    public void afterPublished(GroupAnnouncementResponse announcement, String publisherId) {
        if (announcement == null) {
            return;
        }
        rememberSilently(announcement.getGroupID(), announcement.getAnnouncementID(), publisherId);
        attach(announcement.getGroupID(), publisherId, List.of(announcement));
        AfterCommit.run(() -> publishAnnouncement("published", announcement, publisherId));
    }

    /**
     * 标题或正文变了才清空已读（只改置顶不清）。操作者保持已读。
     */
    @Transactional
    public void afterUpdated(GroupAnnouncementResponse before, GroupAnnouncementResponse after, String updaterId) {
        if (after == null) {
            return;
        }
        boolean contentChanged = before == null
                || !Objects.equals(before.getTitle(), after.getTitle())
                || !Objects.equals(before.getContent(), after.getContent());
        if (contentChanged) {
            readMapper.deleteByAnnouncement(after.getAnnouncementID());
            rememberSilently(after.getGroupID(), after.getAnnouncementID(), updaterId);
        }
        attach(after.getGroupID(), updaterId, List.of(after));
        AfterCommit.run(() -> publishAnnouncement(contentChanged ? "updated" : "pinned", after, updaterId));
    }

    public void afterDeleted(String groupId, String announcementId, String operatorId) {
        readMapper.deleteByAnnouncement(announcementId);
        AfterCommit.run(() -> publishDeleted(groupId, announcementId, operatorId));
    }

    /**
     * 某条公告的置顶状态变了（通常是被另一条挤掉，或被明确取消）。
     * 单独推一帧，在线客户端不用等刷新就能清掉旧的置顶。
     */
    public void publishPin(GroupAnnouncementResponse announcement, String actorId) {
        if (announcement == null || announcement.getAnnouncementID() == null) {
            return;
        }
        attach(announcement.getGroupID(), actorId, List.of(announcement));
        AfterCommit.run(() -> publishAnnouncement("pinned", announcement, actorId));
    }

    /**
     * 列表/详情加载时，给还没有任何已读行的公告补上发布者的已读。
     * 新公告在发布时就会写入发布者；旧公告缺这行，已读人数会一直是 0。
     */
    private void backfillPublishersWithoutReads(String groupId, List<GroupAnnouncementResponse> announcements) {
        List<String> missing = readMapper.listIdsWithoutReads(groupId);
        if (missing == null || missing.isEmpty()) {
            return;
        }
        Set<String> ids = new HashSet<>(missing);
        for (GroupAnnouncementResponse announcement : announcements) {
            if (announcement == null || !ids.contains(announcement.getAnnouncementID())) {
                continue;
            }
            String publisherId = publisherId(announcement);
            if (publisherId == null || !groupMemberService.isGroupMember(groupId, publisherId)) {
                continue;
            }
            rememberSilently(groupId, announcement.getAnnouncementID(), publisherId);
        }
    }

    private void backfillPublisherIfUntracked(String groupId, String announcementId, String viewerId, String publisherId) {
        if (publisherId == null || publisherId.isBlank() || publisherId.equals(viewerId)) {
            return;
        }
        if (readMapper.countByAnnouncement(announcementId) > 0) {
            return;
        }
        if (!groupMemberService.isGroupMember(groupId, publisherId)) {
            return;
        }
        rememberSilently(groupId, announcementId, publisherId);
    }

    private static String publisherId(GroupAnnouncementResponse announcement) {
        if (announcement.getPublisherInfo() == null) {
            return null;
        }
        String publisherId = announcement.getPublisherInfo().getUserId();
        return publisherId == null || publisherId.isBlank() ? null : publisherId;
    }

    private void rememberSilently(String groupId, String announcementId, String userId) {
        if (userId == null || readMapper.countByUser(announcementId, userId) > 0) {
            return;
        }
        try {
            readMapper.insert("anr_" + IdUtil.getSnowflakeNextIdStr(), announcementId, groupId, userId);
        } catch (DuplicateKeyException ignored) {
            // 并发确认时保留先写入的那条
        }
    }

    private Map<String, Integer> readCounts(String groupId) {
        List<AnnouncementReadCountRow> rows = readMapper.countReads(groupId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (rows == null) {
            return counts;
        }
        for (AnnouncementReadCountRow row : rows) {
            if (row != null && row.getAnnouncementId() != null) {
                counts.put(row.getAnnouncementId(), row.getReadCount() == null ? 0 : row.getReadCount());
            }
        }
        return counts;
    }

    private void publishAnnouncement(String action, GroupAnnouncementResponse announcement, String actorId) {
        Map<String, Object> body = objectMapper.convertValue(announcement, new TypeReference<Map<String, Object>>() {});
        body.put("read_by_me", false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("group_id", announcement.getGroupID());
        payload.put("announcement_id", announcement.getAnnouncementID());
        payload.put("actor_id", actorId);
        payload.put("announcement", body);
        publish(STREAM_ANNOUNCEMENT, actorId, payload, memberIds(announcement.getGroupID(), actorId));
    }

    private void publishDeleted(String groupId, String announcementId, String actorId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "deleted");
        payload.put("group_id", groupId);
        payload.put("announcement_id", announcementId);
        payload.put("actor_id", actorId);
        publish(STREAM_ANNOUNCEMENT, actorId, payload, memberIds(groupId, actorId));
    }

    private void publishRead(String groupId, String announcementId, String userId) {
        GroupAnnouncementReadListResponse status = null;
        try {
            if (groupMemberService.isAdminOrOwner(groupId, userId)) {
                status = listReads(groupId, announcementId, userId);
            } else {
                // 人数只该给管理员；这里先算好，投递时再按接收者身份去掉
                List<String> managers = readMapper.listManagerIds(groupId);
                String viewer = managers == null || managers.isEmpty() ? null : managers.getFirst();
                if (viewer != null) {
                    status = listReads(groupId, announcementId, viewer);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("统计公告已读人数失败: announcementId={}, error={}", announcementId, ex.getMessage());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", groupId);
        payload.put("announcement_id", announcementId);
        payload.put("user_id", userId);
        if (status != null) {
            AnnouncementReaderResponse reader = status.getReaders() == null ? null : status.getReaders().stream()
                    .filter(item -> userId.equals(item.getUserId()))
                    .findFirst()
                    .orElse(null);
            if (reader != null) {
                payload.put("username", reader.getUsername());
                payload.put("nickname", reader.getNickname());
                payload.put("avatar_url", reader.getAvatarUrl());
                payload.put("read_at", reader.getReadAt());
            }
            payload.put("read_count", status.getReadCount());
            payload.put("unread_count", status.getUnreadCount());
        }
        java.util.Set<String> receivers = new LinkedHashSet<>();
        List<String> managers = readMapper.listManagerIds(groupId);
        if (managers != null) {
            receivers.addAll(managers);
        }
        receivers.add(userId);
        publish(STREAM_READ, userId, payload, new ArrayList<>(receivers));
    }

    private List<String> memberIds(String groupId, String actorId) {
        List<String> members = readMapper.listActiveMemberIds(groupId);
        java.util.Set<String> ids = new LinkedHashSet<>();
        if (members != null) {
            ids.addAll(members);
        }
        if (actorId != null) {
            ids.add(actorId);
        }
        return new ArrayList<>(ids);
    }

    private void publish(String type, String senderId, Map<String, Object> payload, List<String> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            return;
        }
        try {
            redisStreamService.publishGroupMessage(type, senderId, objectMapper.writeValueAsString(payload), receivers);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("推送群公告通知失败: type={}, error={}", type, ex.getMessage());
        }
    }
}
