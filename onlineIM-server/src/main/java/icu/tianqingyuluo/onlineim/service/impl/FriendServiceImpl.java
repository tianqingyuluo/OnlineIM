package icu.tianqingyuluo.onlineim.service.impl;

import icu.tianqingyuluo.onlineim.mapper.FriendRequestResponseMapper;
import icu.tianqingyuluo.onlineim.mapper.FriendResponseMapper;
import icu.tianqingyuluo.onlineim.mapper.UserBriefResponseMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserFriend;
import icu.tianqingyuluo.onlineim.service.FriendService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FriendServiceImpl implements FriendService {
    private final FriendResponseMapper friendResponseMapper;
    private final UserBriefResponseMapper userBriefResponseMapper;
    private final FriendRequestResponseMapper friendRequestResponseMapper;

    public FriendServiceImpl(FriendResponseMapper friendResponseMapper,UserBriefResponseMapper userBriefResponseMapper,FriendRequestResponseMapper friendRequestResponseMapper) {
        this.friendResponseMapper=friendResponseMapper;
        this.userBriefResponseMapper=userBriefResponseMapper;
        this.friendRequestResponseMapper=friendRequestResponseMapper;
    }

    @Override
    public List<FriendResponse> fetchFriendsByUsername(String username) {
        return friendResponseMapper.getFriendListByUsername(username);
    }

    @Override
    public FriendResponse getByID(String id) {
        return friendResponseMapper.getFriendByID(id);
    }

    @Override
    public List<UserBriefResponse> getBlackListByUsername(String username) {
        return userBriefResponseMapper.getBlackListByUsername(username);
    }

    @Override
    public List<FriendRequestResponse> getFriendRequestListByUserID(String id) {
        return friendRequestResponseMapper.getFriendRequestListByUserID(id);
    }

    @Override
    public List<FriendRequestResponse> getSendFriendRequestListByUserID(String id) {
        return friendRequestResponseMapper.getSendFriendRequestListByUserID(id);
    }

    @Override
    public boolean existFriendByID(String friendId,String userid) {
        return friendRequestResponseMapper.existFriendByID(friendId,userid);
    }

    @Override
    public void updateRemarkByID(String id, String remark) {
        friendRequestResponseMapper.updateRemarkByID(id,remark);
    }

    @Override
    public void updateGroupByID(String id, String groupId) {
        friendRequestResponseMapper.updateGroupByID(id,groupId);
    }

    @Override
    public void updateStatusByID(String id, String status) {
        friendRequestResponseMapper.updateStatusByID(id,status);
    }
}
