package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendRequestRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserFriend;
import org.springframework.stereotype.Service;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

@Service
public interface FriendService {
    List<FriendResponse> fetchFriendsByUsername(String username);
    FriendResponse getByID(String id);
    List<UserBriefResponse> getBlackListByUsername(String username);
    List<FriendRequestResponse> getFriendRequestListByUserID(String id);
    List<FriendRequestResponse> getSendFriendRequestListByUserID(String id);
    boolean existFriendByID(String friendId,String userid);

    /**
     * 双方 user_friends 记录都处于正常好友状态时返回 true。
     * 拉黑、删除或只有单向记录都不算好友。
     */
    boolean areFriends(String userId, String friendUserId);
    void createFriend(UserFriend userFriend, String friendRequestId, int requestStatus, String updatedAt);
    void updateRemarkByID(String id,String remark);
    void updateGroupByID(String id,String groupId);
    void updateStatusByID(String id,String status);
    void sendFriendRequest(String userid, FriendRequestRequest friendRequestRequest) throws SQLIntegrityConstraintViolationException;
}
