package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.GroupMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupSettingMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupSettingUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupSettingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GroupSettingServiceImplTest {

    private GroupMapper groupMapper;
    private GroupSettingMapper settingMapper;
    private GroupSettingServiceImpl service;
    private final GroupSettingResponse settings = GroupSettingResponse.builder().groupId("grp_1").muteType(false).build();

    @BeforeEach
    void setUp() {
        groupMapper = mock(GroupMapper.class);
        settingMapper = mock(GroupSettingMapper.class);
        service = new GroupSettingServiceImpl(groupMapper, mock(GroupMemberMapper.class), settingMapper);
        when(settingMapper.getByGroupId("grp_1")).thenReturn(settings);
        when(groupMapper.isUserMember("grp_1", "usr_member")).thenReturn(true);
        when(groupMapper.isUserMember("grp_1", "usr_admin")).thenReturn(true);
        when(groupMapper.isAdmin("grp_1", "usr_admin")).thenReturn(true);
    }

    @Test
    void ordinaryMemberCanReadSettings() {
        assertSame(settings, service.getGroupSettings("grp_1", "usr_member"));
        assertSame(settings, service.getGroupSettings("grp_1", "usr_admin"));
    }

    @Test
    void nonMemberCannotReadSettings() {
        assertNull(service.getGroupSettings("grp_1", "usr_outsider"));
        verify(settingMapper, never()).getByGroupId(anyString());
    }

    @Test
    void onlyAdminsCanUpdateSettings() {
        assertNull(service.updateGroupSettings("grp_1", new GroupSettingUpdateRequest(), "usr_member"));
        verify(settingMapper, never()).update(anyString(), any());
        assertSame(settings, service.updateGroupSettings("grp_1", new GroupSettingUpdateRequest(), "usr_admin"));
        verify(settingMapper).update(eq("grp_1"), any());
    }
}
