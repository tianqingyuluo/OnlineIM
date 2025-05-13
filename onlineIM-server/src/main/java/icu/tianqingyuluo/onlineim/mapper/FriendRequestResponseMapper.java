package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendRequestResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.UserBriefResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FriendRequestResponseMapper {
    List<FriendRequestResponse> getFriendRequestListByUserID(String id);
    List<FriendRequestResponse> getSendFriendRequestListByUserID(String id);
    boolean existFriendByID(@Param("friendId") String friendId,@Param("userid") String userid);
    @Update("UPDATE user_friends SET remark = #{remark} WHERE id = #{id}")
    void updateRemarkByID(@Param("id") String id, @Param("remark") String remark);
    @Update("UPDATE user_friends SET group_id = #{groupId} WHERE id = #{id}")
    void updateGroupByID(@Param("id") String id, @Param("groupId") String groupId);
    @Update("UPDATE user_friends SET status = #{status} WHERE id = #{id}")
    void updateStatusByID(@Param("id") String id, @Param("status") String status);
}
