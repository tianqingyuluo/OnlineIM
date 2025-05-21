package icu.tianqingyuluo.onlineim.service.impl;

import cn.hutool.core.util.IdUtil;
import icu.tianqingyuluo.onlineim.mapper.*;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupAnnouncementRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupAnnouncementResponse;
import icu.tianqingyuluo.onlineim.service.GroupAnnouncementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupAnnouncementServiceImpl implements GroupAnnouncementService {

    private final GroupMapper groupMapper;
    private final GroupResponseMapper groupResponseMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupSettingMapper groupSettingMapper;
    private final UserMapper userMapper;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupBriefResponseMapper groupBriefResponseMapper;
    private final GroupJoinRequestResponseMapper groupJoinRequestResponseMapper;
    private final GroupAnnouncementMapper groupAnnouncementMapper;

    public GroupAnnouncementServiceImpl(GroupMapper groupMapper, GroupResponseMapper groupResponseMapper, GroupMemberMapper groupMemberMapper, GroupSettingMapper groupSettingMapper, UserMapper userMapper, GroupJoinRequestMapper groupJoinRequestMapper, GroupBriefResponseMapper groupBriefResponseMapper,GroupJoinRequestResponseMapper groupJoinRequestResponseMapper,GroupAnnouncementMapper groupAnnouncementMapper) {
        this.groupMapper = groupMapper;
        this.groupResponseMapper = groupResponseMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupSettingMapper = groupSettingMapper;
        this.userMapper = userMapper;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupBriefResponseMapper = groupBriefResponseMapper;
        this.groupJoinRequestResponseMapper = groupJoinRequestResponseMapper;
        this.groupAnnouncementMapper = groupAnnouncementMapper;
    }
    @Override
    public List<GroupAnnouncementResponse> getAnnouncementsByGroupId(String groupId) {
        return groupAnnouncementMapper.getAnnouncementsByGroupId(groupId);
    }

    @Override
    public GroupAnnouncementResponse getAnnouncementById(String groupId, String announcementId) {
        return groupAnnouncementMapper.getAnnouncementsByAnnouncementId(announcementId,groupId);
    }

    @Override
    @Transactional
    public GroupAnnouncementResponse createAnnouncement(String groupId, GroupAnnouncementRequest request, String publisherId) {
        String id="ann_" + IdUtil.getSnowflakeNextIdStr();
        if(request.getPin()==true){
            groupAnnouncementMapper.cancelPin(groupId);
            groupAnnouncementMapper.insert(id,groupId,publisherId,request);
        }
        return groupAnnouncementMapper.getAnnouncementsByAnnouncementId(id,groupId);
    }

    @Override
    public GroupAnnouncementResponse updateAnnouncement(String groupId, String announcementId, GroupAnnouncementRequest request, String updaterId) {
        if(groupAnnouncementMapper.isSelfAnnouncement(announcementId,groupId)==1){
            if(request.getPin()==true)
                groupAnnouncementMapper.cancelPin(groupId);
            groupAnnouncementMapper.update(announcementId,request);
            return groupAnnouncementMapper.getAnnouncementsByAnnouncementId(announcementId,groupId);
        }
        else
            return null;
    }

    @Override
    public void deleteAnnouncement(String groupId, String announcementId, String deleterId) {
        groupAnnouncementMapper.delete(announcementId);
    }

    @Override
    public void pinAnnouncement(String groupId, String announcementId, Boolean pinStatus, String operatorId) {
        if(groupAnnouncementMapper.isSelfAnnouncement(announcementId,groupId)==1){
            if(pinStatus==true)
                groupAnnouncementMapper.cancelPin(groupId);
            groupAnnouncementMapper.updatePin(announcementId,pinStatus);
        }
    }
}
