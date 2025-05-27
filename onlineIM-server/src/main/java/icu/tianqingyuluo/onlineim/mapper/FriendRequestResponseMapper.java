package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendRequestRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import org.apache.ibatis.annotations.*;
import org.springframework.data.mongodb.repository.ExistsQuery;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

@Mapper
public interface FriendRequestResponseMapper {
    List<FriendRequestResponse> getFriendRequestListByUserID(String id);
    List<FriendRequestResponse> getSendFriendRequestListByUserID(String id);
    @Select("SELECT COUNT(1) > 0 FROM friend_requests " +
            "WHERE from_user_id = #{fromUserId} " +
            "AND to_user_id = #{toUserId} " +
            "AND status = 0")
    boolean existsPendingRequest(
            @Param("fromUserId") String fromUserId,
            @Param("toUserId") String toUserId
    );
    boolean existFriendByID(@Param("friendId") String friendId,@Param("userid") String userid);
    @Update("UPDATE user_friends SET remark = #{remark} WHERE id = #{id}")
    void updateRemarkByID(@Param("id") String id, @Param("remark") String remark);
    @Update("UPDATE user_friends SET group_id = #{groupId} WHERE id = #{id}")
    void updateGroupByID(@Param("id") String id, @Param("groupId") String groupId);
    @Update("UPDATE user_friends SET status = #{status} WHERE id = #{id}")
    void updateStatusByID(@Param("id") String id, @Param("status") String status);
    @Insert("INSERT INTO friend_requests(id, from_user_id, to_user_id, message) " +
            "VALUES(#{id}, #{userid}, #{friendRequestRequest.targetUserId}, #{friendRequestRequest.message})")
    void sendFriendRequest(@Param("id") String id,@Param("userid") String userid,@Param("friendRequestRequest") FriendRequestRequest friendRequestRequest);

}
