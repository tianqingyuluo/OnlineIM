package icu.tianqingyuluo.onlineim.service;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendRequestRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
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
    void updateRemarkByID(String id,String remark);
    void updateGroupByID(String id,String groupId);
    void updateStatusByID(String id,String status);
    void sendFriendRequest(String userid, FriendRequestRequest friendRequestRequest) throws SQLIntegrityConstraintViolationException;
}
