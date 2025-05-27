package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupMemberResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.GroupMember;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 群组成员数据访问接口
 * 提供群组成员相关的数据库操作
 */
@Mapper
public interface GroupMemberMapper {

    /**
     * 根据ID查询群成员
     */
    GroupMemberResponse getGroupMemberByMemberID(String id);

    /**
     * 根据群组成员ID和群组ID查询群成员
     */
    @Select("SELECT COUNT(*) > 0 FROM group_members WHERE id = #{id} AND group_id = #{groupId}")
    boolean isGroupMember(@Param("id") String id, @Param("groupId") String groupId);

    /**
     * 验证memberId 和 userid是否对应
     */
    @Select("SELECT COUNT(*) > 0 FROM group_members WHERE id = #{id} AND user_id = #{userid}")
    boolean isGroupMemberSelf(@Param("id") String id, @Param("userid") String userid);

    /**
     * 根据群组ID和用户ID查询群成员
     */
    @Select("SELECT * FROM group_members WHERE group_id = #{groupId} AND user_id = #{userId}")
    GroupMember getByGroupIdAndUserId(@Param("groupId") String groupId, @Param("userId") String userId);
    
    /**
     * 获取群组的所有成员
     */
    List<GroupMemberResponse> getGroupMembersByGroupID(String groupId);
    
    /**
     * 统计群组的成员数量
     */
    @Select("SELECT COUNT(*) FROM group_members WHERE group_id = #{groupId} AND status = 1")
    int countByGroupId(String groupId);
    
    /**
     * 根据角色查询群成员
     */
    @Select("SELECT * FROM group_members WHERE group_id = #{groupId} AND role = #{role} AND status = 1")
    List<GroupMember> getByGroupIdAndRole(@Param("groupId") String groupId, @Param("role") int role);
    
    /**
     * 新增群成员
     */
    @Insert("INSERT INTO group_members(id, group_id, user_id, nickname, role, mute_end_time, join_time, status, created_at, updated_at) " +
           "VALUES(#{id}, #{groupId}, #{userId}, #{nickname}, #{role}, #{muteEndTime}, #{joinTime}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(GroupMember member);

    /**
     * 新增群成员
     */
    @Insert("INSERT INTO group_members(id,group_id, user_id) " +
            "VALUES(#{id}, #{groupId},#{userid})")
    int insertById(@Param("id") String id,@Param("groupId") String groupId,@Param("userid") String userid);

    /**
     * 更新群成员昵称
     */
    @Update("UPDATE group_members SET nickname = #{nickname} WHERE id = #{id}")
    int updateNickname(@Param("id") String id, @Param("nickname") String nickname);
    
    /**
     * 更新群成员角色
     */
    @Update("UPDATE group_members SET role = #{role} WHERE id = #{id}")
    int updateRole(@Param("id") String id, @Param("role") int role);

    /**
     * 更新群成员角色
     */
    @Update("UPDATE group_members SET role = #{role} WHERE group_id = #{groupId} AND user_id=#{userid}")
    int updateOwnerRole(@Param("groupId") String groupId,@Param("userid") String userid, @Param("role") int role);

    @Update("UPDATE group_members SET role = 2 WHERE user_id=#{userid}")
    void initGroupOwner(String userid);
    /**
     * 更新群成员禁言时间
     */
    @Update("UPDATE group_members SET mute_end_time = #{muteEndTime} WHERE id = #{id}")
    int updateMuteTime(@Param("id") String id, @Param("muteEndTime") LocalDateTime muteEndTime);
    
    /**
     * 更新群成员状态
     */
    @Update("UPDATE group_members SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") int status);
    
    /**
     * 删除群成员
     */
    @Delete("DELETE FROM group_members WHERE id = #{id}")
    int delete(String id);
    
    /**
     * 获取用户加入的所有群组的成员信息（XML实现连表查询）
     */
    List<GroupMember> getUserJoinedGroups(String userId);
    
    /**
     * 获取被禁言的群成员
     */
    @Select("SELECT * FROM group_members WHERE group_id = #{groupId} AND mute_end_time > NOW() AND status = 1")
    List<GroupMember> getMutedMembers(@Param("groupId") String groupId);
    
    /**
     * 批量获取群成员（XML实现，包含IN查询）
     */
    List<GroupMember> batchGetMembersByUserIds(@Param("groupId") String groupId, @Param("userIds") List<String> userIds);
    
    /**
     * 获取群成员及用户信息（XML实现连表查询）
     */
    List<Map<String, Object>> getGroupMembersWithUserInfo(@Param("groupId") String groupId);
}
