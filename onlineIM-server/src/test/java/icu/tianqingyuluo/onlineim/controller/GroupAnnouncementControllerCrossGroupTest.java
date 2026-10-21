package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.mapper.GroupAnnouncementMapper;
import icu.tianqingyuluo.onlineim.service.GroupAnnouncementReadService;
import icu.tianqingyuluo.onlineim.service.GroupAnnouncementService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.service.GroupService;
import icu.tianqingyuluo.onlineim.service.impl.GroupAnnouncementServiceImpl;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 别的群的管理员拿着本群公告 ID，从自己群的路径删除/置顶，都不能生效。 */
class GroupAnnouncementControllerCrossGroupTest {
    private GroupAnnouncementService announcementService;
    private GroupAnnouncementReadService readService;
    private GroupService groupService;
    private GroupAnnouncementController controller;

    @BeforeEach
    void setUp() {
        announcementService = mock(GroupAnnouncementService.class);
        readService = mock(GroupAnnouncementReadService.class);
        groupService = mock(GroupService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.getUserIDFromToken("t")).thenReturn("usr_x");
        // X 是自己群 grp_h 的群主，公告 ann_g 属于 grp_g
        when(groupService.isUserAdminOrOwner("grp_h", "usr_x")).thenReturn(true);
        when(announcementService.getAnnouncementById("grp_h", "ann_g")).thenReturn(null);
        controller = new GroupAnnouncementController(announcementService, readService, jwtUtil, groupService, mock(GroupMemberService.class));
    }

    @Test
    void deleteOtherGroupsAnnouncementIsNotFoundAndTouchesNothing() {
        ResponseEntity<Map<String, String>> r = controller.deleteAnnouncement("t", "grp_h", "ann_g");
        assertEquals(404, r.getStatusCode().value());
        verify(announcementService, never()).deleteAnnouncement(any(), any(), any());
        verify(readService, never()).afterDeleted(any(), any(), any());
    }

    @Test
    void pinOtherGroupsAnnouncementIsNotFound() {
        ResponseEntity<Map<String, String>> r = controller.pinAnnouncement("t", "grp_h", "ann_g", Map.of("pin", true));
        assertEquals(404, r.getStatusCode().value());
        verify(announcementService, never()).pinAnnouncement(any(), any(), anyBoolean(), any());
    }

    @Test
    void serviceDeleteChecksGroupOwnership() {
        GroupAnnouncementMapper mapper = mock(GroupAnnouncementMapper.class);
        when(mapper.isSelfAnnouncement("ann_g", "grp_h")).thenReturn(0);
        GroupAnnouncementServiceImpl service = new GroupAnnouncementServiceImpl(null, null, null, null, null, null, null, null, mapper);
        service.deleteAnnouncement("grp_h", "ann_g", "usr_x");
        verify(mapper, never()).delete(anyString());
        when(mapper.isSelfAnnouncement("ann_g", "grp_g")).thenReturn(1);
        service.deleteAnnouncement("grp_g", "ann_g", "usr_a");
        verify(mapper).delete("ann_g");
    }
}
