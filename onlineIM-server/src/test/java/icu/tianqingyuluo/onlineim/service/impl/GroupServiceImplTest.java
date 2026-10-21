package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.mapper.GroupJoinRequestMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupSettingMapper;
import icu.tianqingyuluo.onlineim.mapper.UserMapper;
import icu.tianqingyuluo.onlineim.pojo.entity.GroupJoinRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupServiceImplTest {

    private GroupMapper groupMapper;
    private GroupMemberMapper groupMemberMapper;
    private GroupSettingMapper groupSettingMapper;
    private UserMapper userMapper;
    private GroupJoinRequestMapper joinRequestMapper;
    private GroupServiceImpl groupService;
    private ConversationRecordService conversationRecords;

    @BeforeEach
    void setUp() {
        groupMapper = mock(GroupMapper.class);
        groupMemberMapper = mock(GroupMemberMapper.class);
        groupSettingMapper = mock(GroupSettingMapper.class);
        userMapper = mock(UserMapper.class);
        joinRequestMapper = mock(GroupJoinRequestMapper.class);
        groupService = new GroupServiceImpl(groupMapper, null, groupMemberMapper, groupSettingMapper,
                userMapper, joinRequestMapper, null, null, null, conversationRecords = mock(ConversationRecordService.class));
    }

    @Test
    void inviteToApprovalGroupStoresPendingStatus() {
        when(userMapper.existsByUserid("usr_c")).thenReturn(true);
        when(groupMapper.existsByGroupId("grp_1")).thenReturn(true);
        when(groupMapper.isNoVerificationToJoin("grp_1")).thenReturn(false);

        assertTrue(groupService.inviteUsersToGroup("grp_1", "usr_c", "usr_a"));

        ArgumentCaptor<GroupJoinRequest> captor = ArgumentCaptor.forClass(GroupJoinRequest.class);
        verify(joinRequestMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        verify(joinRequestMapper).existsPendingByGroupIdAndUserid("grp_1", "usr_c");
    }

    @Test
    void pendingRequestCheckIsScopedToTargetGroup() {
        when(userMapper.existsByUserid("usr_c")).thenReturn(true);
        when(groupMapper.existsByGroupId("grp_2")).thenReturn(true);
        when(groupMapper.isAllowToJoin("grp_2")).thenReturn(true);
        when(joinRequestMapper.existsPendingByGroupIdAndUserid("grp_1", "usr_c")).thenReturn(true);
        when(joinRequestMapper.existsPendingByGroupIdAndUserid("grp_2", "usr_c")).thenReturn(false);

        assertEquals("申请已提交", groupService.requestToJoinGroup("grp_2", "usr_c", "hi"));

        when(groupMapper.existsByGroupId("grp_1")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> groupService.requestToJoinGroup("grp_1", "usr_c", "again"));
    }

    @Test
    void handleJoinRequestDistinguishesForbiddenAndStaleRequests() {
        when(groupMapper.isAdmin("grp_1", "usr_x")).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> groupService.handleJoinRequest("grp_1", "jrq_1", "accept", "usr_x"));

        when(groupMapper.isAdmin("grp_1", "usr_a")).thenReturn(true);
        when(joinRequestMapper.isPendingByRequestId("jrq_other", "grp_1")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> groupService.handleJoinRequest("grp_1", "jrq_other", "accept", "usr_a"));
        verify(groupMemberMapper, never()).insertById(anyString(), anyString(), anyString());

        when(joinRequestMapper.isPendingByRequestId("jrq_1", "grp_1")).thenReturn(true);
        when(joinRequestMapper.getUseridById("jrq_1")).thenReturn("usr_c");
        assertTrue(groupService.handleJoinRequest("grp_1", "jrq_1", "accept", "usr_a"));
        verify(joinRequestMapper).updateStatus("jrq_1", 1);
        // 审批通过后新成员获得自己的群会话记录
        verify(conversationRecords).ensureGroupMembers("grp_1", java.util.List.of("usr_c"));
    }

    @Test
    void createGroupGivesOwnerAndInitialMembersTheirOwnRecords() {
        icu.tianqingyuluo.onlineim.pojo.dto.request.GroupCreateRequest request =
                new icu.tianqingyuluo.onlineim.pojo.dto.request.GroupCreateRequest();
        request.setName("g");
        request.setMemberIDs(java.util.List.of("usr_b", "usr_c"));

        String gid = groupService.createGroup(request, "usr_a");

        verify(conversationRecords).ensureGroupMembers(gid, java.util.Set.of("usr_a", "usr_b", "usr_c"));
    }

    @Test
    void disbandRemovesEveryMembersRecord() {
        when(groupMapper.isGroupOwner("grp_1", "usr_a")).thenReturn(true);
        assertTrue(groupService.disbandGroupByID("grp_1", "usr_a"));
        verify(conversationRecords).removeConversation("grp_1");

        assertFalse(groupService.disbandGroupByID("grp_1", "usr_b"));
        verify(conversationRecords, org.mockito.Mockito.times(1)).removeConversation(anyString());
    }

    @Test
    void quitGroupRemovesMemberOnce() {
        when(groupMapper.isUserMember("grp_1", "usr_b")).thenReturn(true);
        when(groupMemberMapper.deleteByGroupAndUser("grp_1", "usr_b")).thenReturn(1);
        assertTrue(groupService.quitGroup("grp_1", "usr_b"));
        verify(conversationRecords).removeRecord("grp_1", "usr_b");

        when(groupMapper.isUserMember("grp_1", "usr_b")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> groupService.quitGroup("grp_1", "usr_b"));
    }

    @Test
    void nonMemberCannotInvite() {
        when(groupSettingMapper.isAllowMemberInvite("grp_1")).thenReturn(true);
        when(groupMapper.isUserMember("grp_1", "usr_out")).thenReturn(false);
        assertFalse(groupService.canUserInvite("grp_1", "usr_out"));

        when(groupMapper.isUserMember("grp_1", "usr_in")).thenReturn(true);
        assertTrue(groupService.canUserInvite("grp_1", "usr_in"));
    }
}
