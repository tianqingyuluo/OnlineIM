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
     * 路径参数可以是当前用户自己的关系 id（rel_...），也可以是好友用户 id（usr_...）。
     * 找不到属于该用户的关系时返回 null。
     */
    String resolveOwnedFriendshipId(String callerId, String friendOrRelationId);

    /**
     * 双方 user_friends 记录都处于正常好友状态时返回 true。
     * 拉黑、删除或只有单向记录都不算好友。
     */
    boolean areFriends(String userId, String friendUserId);

    /**
     * 删除好友：双向删除好友关系并清理两人之间的好友申请记录。
     * @param callerId 当前用户ID
     * @param friendOrRelationId 好友的用户ID（usr_）或调用者自己的好友关系ID（rel_）
     * @return 被删除好友的用户ID；调用者没有这条好友关系时返回 null
     */
    String deleteFriend(String callerId, String friendOrRelationId);
    void createFriend(UserFriend userFriend, String friendRequestId, int requestStatus, String updatedAt);
    void updateRemarkByID(String id,String remark);
    void updateGroupByID(String id,String groupId);
    void updateStatusByID(String id,String status);
    void sendFriendRequest(String userid, FriendRequestRequest friendRequestRequest) throws SQLIntegrityConstraintViolationException;
}
