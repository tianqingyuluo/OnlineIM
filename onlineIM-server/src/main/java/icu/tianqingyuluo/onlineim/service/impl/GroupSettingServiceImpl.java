package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.*;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupSettingUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupSettingResponse;
import icu.tianqingyuluo.onlineim.service.GroupSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupSettingServiceImpl implements GroupSettingService {
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupSettingMapper groupSettingMapper;

    public GroupSettingServiceImpl(GroupMapper groupMapper, GroupMemberMapper groupMemberMapper, GroupSettingMapper groupSettingMapper) {
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupSettingMapper = groupSettingMapper;
    }
    @Override
    public GroupSettingResponse getGroupSettings(String groupId, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            return groupSettingMapper.getByGroupId(groupId);
        }
        else
            return null;
    }

    @Override
    public GroupSettingResponse updateGroupSettings(String groupId, GroupSettingUpdateRequest request, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            groupSettingMapper.update(groupId,request);
            return groupSettingMapper.getByGroupId(groupId);
        }
        else
            return null;
    }

    @Override
    public GroupSettingResponse muteAll(String groupId, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            int muteType=1;
            groupSettingMapper.updateMuteType(groupId,muteType);
            return groupSettingMapper.getByGroupId(groupId);
        }
        else
            return null;
    }

    @Override
    public GroupSettingResponse unmuteAll(String groupId, String operatorId) {
        if(groupMapper.isAdmin(groupId,operatorId)){
            int muteType=0;
            groupSettingMapper.updateMuteType(groupId,muteType);
            return groupSettingMapper.getByGroupId(groupId);
        }
        else
            return null;
    }
}
