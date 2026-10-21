package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.exception.ForbiddenException;
import icu.tianqingyuluo.onlineim.mapper.GroupMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupSettingMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import icu.tianqingyuluo.onlineim.util.AfterCommit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class GroupMemberServiceImpl implements GroupMemberService {
    private static final int ROLE_MEMBER = 0;
    private static final int ROLE_ADMIN = 1;
    private static final int ROLE_OWNER = 2;
    private static final DateTimeFormatter UPDATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupSettingMapper groupSettingMapper;
    private final ConversationRecordService conversationRecords;

    public GroupMemberServiceImpl(GroupMapper groupMapper, GroupMemberMapper groupMemberMapper,
                                  GroupSettingMapper groupSettingMapper, ConversationRecordService conversationRecords) {
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupSettingMapper = groupSettingMapper;
        this.conversationRecords = conversationRecords;
    }

    @Override
    public List<GroupMemberResponse> getGroupMembers(String groupId) {
        return groupMemberMapper.getGroupMembersByGroupID(groupId);
    }

    @Override
    public boolean isAdminOrOwner(String groupId, String userId) {
        if (groupId == null || userId == null || userId.isBlank()) {
            return false;
        }
        return groupMapper.isGroupOwner(groupId, userId) || groupMapper.isAdmin(groupId, userId);
    }

    @Override
    public GroupMemberResponse getMemberDetail(String groupId, String memberId, String operatorId) {
        if(groupMapper.isUserMember(groupId,operatorId)){
            return groupMemberMapper.getGroupMemberByMemberID(memberId, groupId);
        }
        else
            return null;
    }

    @Override
    public boolean removeMember(String groupId, String memberId, String operatorId) {
        if(!groupMapper.isAdmin(groupId,operatorId)){
            throw new ForbiddenException("无权限移除群成员");
        }
        String removedUserId = groupMemberMapper.getUserIdByMemberId(memberId, groupId);
        boolean removed = groupMemberMapper.delete(memberId, groupId)==1;
        if (removed && removedUserId != null && conversationRecords != null) {
            // 被移出的成员不再拥有该群的会话记录
            AfterCommit.run(() -> conversationRecords.removeRecord(groupId, removedUserId));
        }
        return removed;
    }

    @Override
    public boolean setAdmin(String groupId, String memberId, String operatorId) {
        return changeAdminRole(groupId, memberId, operatorId, ROLE_ADMIN);
    }

    @Override
    public boolean cancelAdmin(String groupId, String memberId, String operatorId) {
        return changeAdminRole(groupId, memberId, operatorId, ROLE_MEMBER);
    }

    private boolean changeAdminRole(String groupId, String memberId, String operatorId, int role) {
        if (!groupMapper.isGroupOwner(groupId, operatorId)) {
            throw new ForbiddenException("只有群主可以设置或取消管理员");
        }
        Integer targetRole = groupMemberMapper.getRoleByMemberId(memberId, groupId);
        if (targetRole == null) {
            throw new IllegalArgumentException("群成员不存在");
        }
        if (targetRole == ROLE_OWNER) {
            throw new IllegalArgumentException("不能修改群主的角色");
        }
        return groupMemberMapper.updateRole(memberId, groupId, role) == 1;
    }

    @Override
    public boolean updateMemberNickname(String groupId, String memberId, String nickname, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            return groupMemberMapper.updateNickname(memberId, groupId, nickname)==1;
        }
        else if(groupMapper.isUserMember(groupId,operatorId) && groupMemberMapper.isGroupMemberSelf(memberId,operatorId)){
            return groupMemberMapper.updateNickname(memberId, groupId, nickname)==1;
        }
        else
            return false;
    }

    @Override
    public boolean muteMember(String groupId, String memberId, Integer duration, String operatorId) {
        assertCanMute(groupId, memberId, operatorId);
        LocalDateTime future = LocalDateTime.now().plusMinutes(duration);
        return groupMemberMapper.updateMuteTime(memberId, groupId, future) == 1;
    }

    @Override
    public boolean unmuteMember(String groupId, String memberId, String operatorId) {
        assertCanMute(groupId, memberId, operatorId);
        return groupMemberMapper.updateMuteTime(memberId, groupId, null) == 1;
    }

    /**
     * 禁言/解除禁言权限：操作者必须是管理员或群主，且只能操作角色比自己低的成员。
     */
    private void assertCanMute(String groupId, String memberId, String operatorId) {
        Integer operatorRole = groupMemberMapper.getRoleByUserId(groupId, operatorId);
        if (operatorRole == null || operatorRole < ROLE_ADMIN) {
            throw new ForbiddenException("只有群主或管理员可以禁言成员");
        }
        Integer targetRole = groupMemberMapper.getRoleByMemberId(memberId, groupId);
        if (targetRole == null) {
            throw new IllegalArgumentException("群成员不存在");
        }
        if (targetRole >= operatorRole) {
            throw new ForbiddenException("不能禁言同级或更高角色的成员");
        }
    }

    @Override
    @Transactional
    public boolean transferOwnership(String groupId, String memberId, String operatorId) {
        if (!groupMapper.isGroupOwner(groupId, operatorId)) {
            throw new ForbiddenException("只有群主可以转让群主身份");
        }
        String newOwnerId = groupMemberMapper.getUserIdByMemberId(memberId, groupId);
        if (newOwnerId == null) {
            throw new IllegalArgumentException("群成员不存在");
        }
        if (newOwnerId.equals(operatorId)) {
            throw new IllegalArgumentException("不能转让给自己");
        }
        String updatedAt = LocalDateTime.now().format(UPDATED_AT_FORMATTER);
        if (groupMemberMapper.updateRole(memberId, groupId, ROLE_OWNER) != 1
                || groupMemberMapper.updateOwnerRole(groupId, operatorId, ROLE_MEMBER) != 1
                || groupMapper.updateOwner(groupId, newOwnerId, updatedAt) != 1) {
            throw new IllegalStateException("转让群主失败");
        }
        return true;
    }

    @Override
    public boolean isGroupMember(String groupId, String userid) {
        return groupMapper.isUserMember(groupId,userid);
    }
}
