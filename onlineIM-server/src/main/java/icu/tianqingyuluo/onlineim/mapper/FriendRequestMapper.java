package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.FriendRequest;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 好友请求数据访问接口
 */
@Mapper
public interface FriendRequestMapper {

    @Select("SELECT * FROM friend_requests WHERE id = #{id}")
    FriendRequest getById(String id);
    
    @Select("SELECT * FROM friend_requests WHERE to_user_id = #{userId} AND status = 0 ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<FriendRequest> getReceivedRequests(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);
    
    @Select("SELECT COUNT(*) FROM friend_requests WHERE to_user_id = #{userId} AND status = 0")
    int countReceivedRequests(String userId);
    
    @Select("SELECT * FROM friend_requests WHERE from_user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<FriendRequest> getSentRequests(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);
    
    @Insert("INSERT INTO friend_requests(id, from_user_id, to_user_id, message, status, created_at, updated_at) " +
            "VALUES(#{id}, #{fromUserId}, #{toUserId}, #{message}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(FriendRequest friendRequest);
    
    @Update("UPDATE friend_requests SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") int status, @Param("updatedAt") String updatedAt);
    
    @Select("SELECT * FROM friend_requests WHERE from_user_id = #{fromUserId} AND to_user_id = #{toUserId} AND status = 0")
    FriendRequest getPendingRequest(@Param("fromUserId") String fromUserId, @Param("toUserId") String toUserId);
} 