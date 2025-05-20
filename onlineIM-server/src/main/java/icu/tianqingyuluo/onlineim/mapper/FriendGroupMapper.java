package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.FriendGroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.FriendGroupResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.FriendGroup;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 好友分组数据访问接口
 */
@Mapper
public interface FriendGroupMapper {

    List<FriendGroupResponse> getFriendGroupsByUserId(String userId);

    FriendGroupResponse getFriendGroupByGroupId(String id);

    @Select("SELECT * FROM friend_groups WHERE id = #{id}")
    FriendGroup getById(String id);
    
    @Select("SELECT * FROM friend_groups WHERE user_id = #{userId} ORDER BY sort ASC")
    List<FriendGroup> getUserGroups(String userId);

    @Select("SELECT count(*)>0 FROM friend_groups WHERE user_id = #{userid} AND id = #{id}")
    boolean isYourGroup(@Param("userid") String userid,@Param("id") String id);

    @Insert("INSERT INTO friend_groups(id, user_id, name, sort) " +
            "VALUES(#{id}, #{userid}, #{request.name}, #{request.sort})")
    int insert(@Param("id") String id,@Param("userid") String userid,@Param("request") FriendGroupCreateRequest request);

    @Update("UPDATE friend_groups SET name = #{request.name}, sort = #{request.sort} WHERE id = #{id}")
    int update(@Param("id") String id,@Param("request") FriendGroupUpdateRequest request);
    
    @Delete("DELETE FROM friend_groups WHERE id = #{id}")
    int delete(String id);
    
    @Select("SELECT COUNT(*) FROM user_friends WHERE group_id = #{groupId}")
    int countFriendsInGroup(String groupId);
} 