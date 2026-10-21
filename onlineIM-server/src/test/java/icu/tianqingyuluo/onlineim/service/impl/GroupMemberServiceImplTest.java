package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.mapper.GroupMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupMemberServiceImplTest {

    private GroupMapper groupMapper;
    private GroupMemberMapper groupMemberMapper;
    private GroupMemberServiceImpl service;

    @BeforeEach
    void setUp() {
        groupMapper = mock(GroupMapper.class);
        groupMemberMapper = mock(GroupMemberMapper.class);
        service = new GroupMemberServiceImpl(groupMapper, groupMemberMapper, null);
    }

    @Test
    void transferOwnershipAlsoUpdatesGroupOwnerId() {
        when(groupMapper.isGroupOwner("grp_1", "usr_owner")).thenReturn(true);
        when(groupMemberMapper.getUserIdByMemberId("mem_b", "grp_1")).thenReturn("usr_b");
        when(groupMemberMapper.updateRole("mem_b", "grp_1", 2)).thenReturn(1);
        when(groupMemberMapper.updateOwnerRole("grp_1", "usr_owner", 0)).thenReturn(1);
        when(groupMapper.updateOwner(eq("grp_1"), eq("usr_b"), anyString())).thenReturn(1);

        assertTrue(service.transferOwnership("grp_1", "mem_b", "usr_owner"));

        verify(groupMapper).updateOwner(eq("grp_1"), eq("usr_b"), anyString());
    }

    @Test
    void onlyOwnerCanTransferOrChangeAdmins() {
        assertThrows(ForbiddenException.class, () -> service.transferOwnership("grp_1", "mem_b", "usr_x"));
        assertThrows(ForbiddenException.class, () -> service.setAdmin("grp_1", "mem_b", "usr_x"));
        assertThrows(ForbiddenException.class, () -> service.cancelAdmin("grp_1", "mem_b", "usr_x"));
        verify(groupMemberMapper, never()).updateRole(anyString(), anyString(), anyInt());
    }

    @Test
    void ownerRoleCannotBeChangedThroughAdminEndpoints() {
        when(groupMapper.isGroupOwner("grp_1", "usr_owner")).thenReturn(true);
        when(groupMemberMapper.getRoleByMemberId("mem_owner", "grp_1")).thenReturn(2);

        assertThrows(IllegalArgumentException.class, () -> service.cancelAdmin("grp_1", "mem_owner", "usr_owner"));
        verify(groupMemberMapper, never()).updateRole(anyString(), anyString(), anyInt());
    }

    @Test
    void muteRequiresHigherRoleThanTarget() {
        when(groupMemberMapper.getRoleByUserId("grp_1", "usr_member")).thenReturn(0);
        when(groupMemberMapper.getRoleByUserId("grp_1", "usr_admin")).thenReturn(1);
        when(groupMemberMapper.getRoleByMemberId("mem_member", "grp_1")).thenReturn(0);
        when(groupMemberMapper.getRoleByMemberId("mem_owner", "grp_1")).thenReturn(2);
        when(groupMemberMapper.updateMuteTime(eq("mem_member"), eq("grp_1"), any())).thenReturn(1);
        when(groupMemberMapper.updateMuteTime(eq("mem_member"), eq("grp_1"), isNull())).thenReturn(1);

        assertThrows(ForbiddenException.class, () -> service.muteMember("grp_1", "mem_member", 10, "usr_member"));
        assertThrows(ForbiddenException.class, () -> service.muteMember("grp_1", "mem_owner", 10, "usr_admin"));
        assertThrows(ForbiddenException.class, () -> service.muteMember("grp_1", "mem_member", 10, "usr_outsider"));
        assertTrue(service.muteMember("grp_1", "mem_member", 10, "usr_admin"));
        assertTrue(service.unmuteMember("grp_1", "mem_member", "usr_admin"));
    }
}
