package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.FriendGroup;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 好友分组数据访问接口
 */
@Mapper
public interface FriendGroupMapper {

    @Select("SELECT * FROM friend_groups WHERE id = #{id}")
    FriendGroup getById(String id);
    
    @Select("SELECT * FROM friend_groups WHERE user_id = #{userId} ORDER BY sort ASC")
    List<FriendGroup> getUserGroups(String userId);
    
    @Insert("INSERT INTO friend_groups(id, user_id, name, sort, created_at, updated_at) " +
            "VALUES(#{id}, #{userId}, #{name}, #{sort}, #{createdAt}, #{updatedAt})")
    int insert(FriendGroup friendGroup);
    
    @Update("UPDATE friend_groups SET name = #{name}, sort = #{sort}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(FriendGroup friendGroup);
    
    @Delete("DELETE FROM friend_groups WHERE id = #{id}")
    int delete(String id);
    
    @Select("SELECT COUNT(*) FROM user_friends WHERE group_id = #{groupId}")
    int countFriendsInGroup(String groupId);
} 