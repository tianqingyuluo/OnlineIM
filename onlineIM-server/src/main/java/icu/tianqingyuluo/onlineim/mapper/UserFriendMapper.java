package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.UserFriend;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 好友关系数据访问接口
 */
@Mapper
public interface UserFriendMapper {

    @Select("SELECT * FROM user_friends WHERE id = #{id}")
    UserFriend getById(String id);
    
    @Select("SELECT * FROM user_friends WHERE user_id = #{userId} AND friend_id = #{friendId}")
    UserFriend getByUserIdAndFriendId(@Param("userId") String userId, @Param("friendId") String friendId);
    
    @Select("SELECT * FROM user_friends WHERE user_id = #{userId} AND status = 1 ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<UserFriend> getUserFriends(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);
    
    @Select("SELECT COUNT(*) FROM user_friends WHERE user_id = #{userId} AND status = 1")
    int countUserFriends(String userId);
    
    @Insert("INSERT INTO user_friends(id, user_id, friend_id, remark, group_id, status, created_at, updated_at) " +
            "VALUES(#{id}, #{userId}, #{friendId}, #{remark}, #{groupId}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(UserFriend userFriend);
    
    @Update("UPDATE user_friends SET remark = #{remark}, group_id = #{groupId}, updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int update(UserFriend userFriend);
    
    @Update("UPDATE user_friends SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") int status, @Param("updatedAt") String updatedAt);
    
    @Delete("DELETE FROM user_friends WHERE id = #{id}")
    int delete(String id);

    @Select("SELECT friend_id FROM user_friends WHERE id = #{id} AND user_id = #{userId}")
    String getFriendIdByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    @Delete("DELETE FROM user_friends WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int deleteByUserIdAndFriendId(@Param("userId") String userId, @Param("friendId") String friendId);
    
    @Select("SELECT * FROM user_friends WHERE user_id = #{userId} AND status = 2")
    List<UserFriend> getBlacklist(String userId);
    
    @Select("SELECT remark FROM user_friends WHERE user_id = #{userId} AND friend_id = #{friendId} AND status = 1")
    String getRemarkByUserIdAndFriendId(@Param("userId") String userId, @Param("friendId") String friendId);

    /** 双方都是正常好友（status=1）。单向关系、拉黑、已删除都不算。 */
    @Select("""
            SELECT a.friend_id
            FROM user_friends a
            JOIN user_friends b
              ON b.user_id = a.friend_id AND b.friend_id = a.user_id AND b.status = 1
            WHERE a.user_id = #{userId} AND a.status = 1
            """)
    List<String> listMutualFriendIds(@Param("userId") String userId);
}