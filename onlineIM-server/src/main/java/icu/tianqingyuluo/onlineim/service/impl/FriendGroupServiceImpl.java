package icu.tianqingyuluo.onlineim.service.impl;

import cn.hutool.core.util.IdUtil;
import icu.tianqingyuluo.onlineim.mapper.FriendGroupMapper;
import icu.tianqingyuluo.onlineim.mapper.FriendRequestResponseMapper;
import icu.tianqingyuluo.onlineim.mapper.FriendResponseMapper;
import icu.tianqingyuluo.onlineim.mapper.UserBriefResponseMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendGroupResponse;
import icu.tianqingyuluo.onlineim.service.FriendGroupService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FriendGroupServiceImpl implements FriendGroupService {

    private final FriendResponseMapper friendResponseMapper;
    private final UserBriefResponseMapper userBriefResponseMapper;
    private final FriendRequestResponseMapper friendRequestResponseMapper;
    private final FriendGroupMapper friendGroupMapper;

    public FriendGroupServiceImpl(FriendResponseMapper friendResponseMapper, UserBriefResponseMapper userBriefResponseMapper, FriendRequestResponseMapper friendRequestResponseMapper,FriendGroupMapper friendGroupMapper) {
        this.friendResponseMapper=friendResponseMapper;
        this.userBriefResponseMapper=userBriefResponseMapper;
        this.friendRequestResponseMapper=friendRequestResponseMapper;
        this.friendGroupMapper=friendGroupMapper;
    }

    @Override
    public List<FriendGroupResponse> getFriendGroupsByUserId(String userId) throws Exception {
        try {
            return friendGroupMapper.getFriendGroupsByUserId(userId);
        } catch (DataAccessException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    @Override
    public FriendGroupResponse createFriendGroup(String userId, FriendGroupCreateRequest request) throws Exception {
        try {
            String id="fgrp_" + IdUtil.getSnowflakeNextIdStr();
            if(friendGroupMapper.insert(id,userId,request)==1)
                return friendGroupMapper.getFriendGroupByGroupId(id);
            else
                throw new IllegalArgumentException("请求被拒绝");
        } catch (DataAccessException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    @Override
    public FriendGroupResponse updateFriendGroup(String userId, String groupId, FriendGroupUpdateRequest request) throws Exception {
        try {
            if(friendGroupMapper.isYourGroup(userId,groupId)&&friendGroupMapper.update(groupId,request)==1)
                return friendGroupMapper.getFriendGroupByGroupId(groupId);
            else
                throw new IllegalArgumentException("请求被拒绝");
        } catch (DataAccessException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    @Override
    public boolean deleteFriendGroup(String userId, String groupId) throws Exception {
        try {
            if(friendGroupMapper.isYourGroup(userId,groupId)&&friendGroupMapper.delete(groupId)==1)
                return true;
            else
                throw new IllegalArgumentException("请求被拒绝");
        } catch (DataAccessException e) {
            throw new RuntimeException("数据库操作失败", e);
        }
    }

    @Override
    public boolean sortFriendGroups(String userId, List<Map<String, Object>> request) throws Exception {
        return false;
    }
}
