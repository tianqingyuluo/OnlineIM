package icu.tianqingyuluo.onlineim.service.impl;

import cn.hutool.core.util.IdUtil;
import icu.tianqingyuluo.onlineim.mapper.*;
import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendRequestRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserFriend;
import icu.tianqingyuluo.onlineim.service.FriendService;
import icu.tianqingyuluo.onlineim.util.enumeration.FriendshipStatusEnum;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Date;
import java.util.List;

@Service
public class FriendServiceImpl implements FriendService {
    private final FriendResponseMapper friendResponseMapper;
    private final UserBriefResponseMapper userBriefResponseMapper;
    private final FriendRequestResponseMapper friendRequestResponseMapper;
    private final UserFriendMapper userFriendMapper;
    private final FriendRequestMapper friendRequestMapper;

    public FriendServiceImpl(FriendResponseMapper friendResponseMapper, UserBriefResponseMapper userBriefResponseMapper, FriendRequestResponseMapper friendRequestResponseMapper, UserFriendMapper userFriendMapper, FriendRequestMapper friendRequestMapper) {
        this.friendResponseMapper=friendResponseMapper;
        this.userBriefResponseMapper=userBriefResponseMapper;
        this.friendRequestResponseMapper=friendRequestResponseMapper;
        this.userFriendMapper = userFriendMapper;
        this.friendRequestMapper = friendRequestMapper;
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
    public String resolveOwnedFriendshipId(String callerId, String friendOrRelationId) {
        if (callerId == null || friendOrRelationId == null || callerId.isBlank() || friendOrRelationId.isBlank()) {
            return null;
        }
        if (friendRequestResponseMapper.existFriendByID(friendOrRelationId, callerId)) {
            return friendOrRelationId;
        }
        UserFriend relation = userFriendMapper.getByUserIdAndFriendId(callerId, friendOrRelationId);
        if (relation == null || relation.getId() == null || relation.getId().isBlank()) {
            return null;
        }
        return relation.getId();
    }

    @Override
    public boolean areFriends(String userId, String friendUserId) {
        if (userId == null || friendUserId == null || userId.isBlank() || friendUserId.isBlank()
                || userId.equals(friendUserId)) {
            return false;
        }
        return isNormalFriend(userId, friendUserId) && isNormalFriend(friendUserId, userId);
    }

    private boolean isNormalFriend(String userId, String friendUserId) {
        UserFriend relation = userFriendMapper.getByUserIdAndFriendId(userId, friendUserId);
        return relation != null
                && relation.getStatus() != null
                && relation.getStatus() == FriendshipStatusEnum.NORMAL.getValue();
    }

    @Override
    @Transactional
    public void createFriend(UserFriend userFriend, String friendRequestId, int requestStatus, String updatedAt) {
        friendRequestMapper.updateStatus(friendRequestId, requestStatus, updatedAt);
        userFriendMapper.insert(userFriend);
        String temp = userFriend.getUserId();
        userFriend.setUserId(userFriend.getFriendId());
        userFriend.setFriendId(temp);
        userFriend.setId("rel_" + IdUtil.getSnowflakeNextIdStr());
        userFriendMapper.insert(userFriend);
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

    @Override
    public void sendFriendRequest(String userid, FriendRequestRequest friendRequestRequest) throws SQLIntegrityConstraintViolationException{
        if(!friendRequestResponseMapper.existsPendingRequest(userid,friendRequestRequest.getTargetUserId())){
            String friendRequestID = "req_" + IdUtil.getSnowflakeNextIdStr();
            friendRequestResponseMapper.sendFriendRequest(friendRequestID,userid,friendRequestRequest);
        }
        else{
            throw new SQLIntegrityConstraintViolationException("好友申请已存在");
        }
    }
}
