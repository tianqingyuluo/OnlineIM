package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.GroupMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupMemberMapper;
import icu.tianqingyuluo.onlineim.mapper.GroupSettingMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.service.GroupMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class GroupMemberServiceImpl implements GroupMemberService {
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupSettingMapper groupSettingMapper;

    public GroupMemberServiceImpl(GroupMapper groupMapper, GroupMemberMapper groupMemberMapper, GroupSettingMapper groupSettingMapper) {
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupSettingMapper = groupSettingMapper;
    }

    @Override
    public GroupMemberResponse getMemberDetail(String groupId, String memberId, String operatorId) {
        if(groupMapper.isUserMember(groupId,operatorId)){
            return groupMemberMapper.getGroupMemberByMemberID(memberId);
        }
        else
            return null;
    }

    @Override
    public boolean removeMember(String groupId, String memberId, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            return groupMemberMapper.delete(memberId)==1;
        }
        else
            return false;
    }

    @Override
    public boolean setAdmin(String groupId, String memberId, String operatorId) {
        if(groupMapper.isGroupOwner(groupId,operatorId)){
            int role=1;
            return groupMemberMapper.updateRole(memberId,role)==1;
        }
        else
            return false;
    }

    @Override
    public boolean cancelAdmin(String groupId, String memberId, String operatorId) {
        if(groupMapper.isGroupOwner(groupId,operatorId)){
            int role=0;
            return groupMemberMapper.updateRole(memberId,role)==1;
        }
        else
            return false;
    }

    @Override
    public boolean updateMemberNickname(String groupId, String memberId, String nickname, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            return groupMemberMapper.updateNickname(memberId,nickname)==1;
        }
        else if(groupMapper.isUserMember(groupId,operatorId) && groupMemberMapper.isGroupMemberSelf(memberId,operatorId)){
            return groupMemberMapper.updateNickname(memberId,nickname)==1;
        }
        else
            return false;
    }

    @Override
    public boolean muteMember(String groupId, String memberId, Integer duration, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime future = now.plusMinutes(duration);
            return groupMemberMapper.updateMuteTime(memberId, future)==1;
        }
        else
            return false;
    }

    @Override
    public boolean unmuteMember(String groupId, String memberId, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            return groupMemberMapper.updateMuteTime(memberId, null)==1;
        }
        else
            return false;
    }

    @Override
    @Transactional
    public boolean transferOwnership(String groupId, String memberId, String operatorId) {
        if(groupMapper.isGroupOwner(groupId,operatorId)){
            int memberRole = 0;
            int ownerRole = 2;
            return groupMemberMapper.updateRole(memberId,ownerRole)==1&&groupMemberMapper.updateOwnerRole(groupId,operatorId,memberRole)==1;
        }
        else
            return false;
    }
}
