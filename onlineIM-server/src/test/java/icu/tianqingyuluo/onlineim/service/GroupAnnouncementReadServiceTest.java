package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.mapper.GroupAnnouncementReadMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.AnnouncementReadCountRow;
import icu.tianqingyuluo.onlineim.pojo.dto.response.AnnouncementReaderResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupAnnouncementReadListResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupAnnouncementResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupAnnouncementReadServiceTest {

    private GroupAnnouncementReadMapper readMapper;
    private GroupMemberService groupMemberService;
    private RedisStreamService redis;
    private GroupAnnouncementReadService service;

    @BeforeEach
    void setUp() {
        readMapper = mock(GroupAnnouncementReadMapper.class);
        groupMemberService = mock(GroupMemberService.class);
        redis = mock(RedisStreamService.class);
        service = new GroupAnnouncementReadService(readMapper, groupMemberService, redis, new ObjectMapper());
        when(groupMemberService.isGroupMember("grp_1", "usr_member")).thenReturn(true);
        when(groupMemberService.isGroupMember("grp_1", "usr_admin")).thenReturn(true);
        when(groupMemberService.isAdminOrOwner("grp_1", "usr_admin")).thenReturn(true);
        when(groupMemberService.isAdminOrOwner("grp_1", "usr_member")).thenReturn(false);
        when(readMapper.listActiveMemberIds("grp_1")).thenReturn(List.of("usr_admin", "usr_member"));
        when(readMapper.listManagerIds("grp_1")).thenReturn(List.of("usr_admin"));
    }

    @Test
    void memberConfirmIsIdempotentAndNotifiesManagers() {
        when(readMapper.countByUser("ann_1", "usr_member")).thenReturn(0, 1);
        when(readMapper.listReaders("ann_1")).thenReturn(List.of(reader("usr_member")));
        when(readMapper.listUnreadMembers("grp_1", "ann_1")).thenReturn(List.of(reader("usr_admin")));

        assertTrue(service.markRead("grp_1", "ann_1", "usr_member"));
        assertFalse(service.markRead("grp_1", "ann_1", "usr_member"));

        verify(readMapper, times(1)).insert(startsWith("anr_"), eq("ann_1"), eq("grp_1"), eq("usr_member"));
        verify(redis).publishGroupMessage(eq(GroupAnnouncementReadService.STREAM_READ), eq("usr_member"),
                argThat(body -> body.contains("\"read_count\":1") && body.contains("usr_member")),
                eq(List.of("usr_admin", "usr_member")));
    }

    @Test
    void duplicateInsertDoesNotPublish() {
        when(readMapper.countByUser("ann_1", "usr_member")).thenReturn(0);
        when(readMapper.insert(anyString(), eq("ann_1"), eq("grp_1"), eq("usr_member")))
                .thenThrow(new DuplicateKeyException("dup"));

        assertFalse(service.markRead("grp_1", "ann_1", "usr_member"));
        verifyNoInteractions(redis);
    }

    @Test
    void outsiderCannotConfirmOrListReads() {
        when(groupMemberService.isGroupMember("grp_1", "usr_out")).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.markRead("grp_1", "ann_1", "usr_out"));
        assertThrows(ForbiddenException.class, () -> service.listReads("grp_1", "ann_1", "usr_member"));
    }

    @Test
    void contentChangeClearsReadsButPinDoesNot() {
        GroupAnnouncementResponse before = announcement("旧标题", "旧内容");
        GroupAnnouncementResponse after = announcement("新标题", "旧内容");
        when(readMapper.listReadAnnouncementIds("grp_1", "usr_admin")).thenReturn(List.of("ann_1"));
        when(readMapper.countReads("grp_1")).thenReturn(List.of(count("ann_1", 1)));
        when(readMapper.countActiveMembers("grp_1")).thenReturn(2);

        service.afterUpdated(before, after, "usr_admin");

        verify(readMapper).deleteByAnnouncement("ann_1");
        verify(readMapper).insert(startsWith("anr_"), eq("ann_1"), eq("grp_1"), eq("usr_admin"));
        assertEquals(Boolean.TRUE, after.getReadByMe());
        assertEquals(1, after.getReadCount());
        assertEquals(1, after.getUnreadCount());
        verify(redis).publishGroupMessage(eq(GroupAnnouncementReadService.STREAM_ANNOUNCEMENT), eq("usr_admin"),
                argThat(body -> body.contains("\"action\":\"updated\"") && body.contains("\"read_by_me\":false")),
                eq(List.of("usr_admin", "usr_member")));

        clearInvocations(readMapper, redis);
        service.afterUpdated(after, announcement("新标题", "旧内容"), "usr_admin");
        verify(readMapper, never()).deleteByAnnouncement(anyString());
        verify(redis).publishGroupMessage(eq(GroupAnnouncementReadService.STREAM_ANNOUNCEMENT), eq("usr_admin"),
                argThat(body -> body.contains("\"action\":\"pinned\"")), any());
    }

    @Test
    void regularMembersDoNotSeeCountsOnTheList() {
        GroupAnnouncementResponse announcement = announcement("t", "c");
        when(readMapper.listReadAnnouncementIds("grp_1", "usr_member")).thenReturn(List.of());

        service.attach("grp_1", "usr_member", List.of(announcement));

        assertEquals(Boolean.FALSE, announcement.getReadByMe());
        assertNull(announcement.getReadCount());
        assertNull(announcement.getUnreadCount());
        verify(readMapper, never()).countReads(anyString());
    }

    @Test
    void managersSeeWhoRead() {
        when(readMapper.listReaders("ann_1")).thenReturn(List.of(reader("usr_admin")));
        when(readMapper.listUnreadMembers("grp_1", "ann_1")).thenReturn(List.of(reader("usr_member")));

        GroupAnnouncementReadListResponse status = service.listReads("grp_1", "ann_1", "usr_admin");

        assertEquals(1, status.getReadCount());
        assertEquals(1, status.getUnreadCount());
        assertEquals("usr_member", status.getUnreadUsers().getFirst().getUserId());
    }

    private static GroupAnnouncementResponse announcement(String title, String content) {
        return GroupAnnouncementResponse.builder()
                .announcementID("ann_1").groupID("grp_1").title(title).content(content).build();
    }

    private static AnnouncementReaderResponse reader(String userId) {
        return AnnouncementReaderResponse.builder().userId(userId).nickname(userId).readAt("2026-10-21 12:00:00").build();
    }

    private static AnnouncementReadCountRow count(String id, int readCount) {
        AnnouncementReadCountRow row = new AnnouncementReadCountRow();
        row.setAnnouncementId(id);
        row.setReadCount(readCount);
    return row;
    }
}
