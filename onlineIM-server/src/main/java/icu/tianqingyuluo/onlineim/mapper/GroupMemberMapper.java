package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.GroupMember;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface GroupMemberMapper {
    @Select("SELECT * FROM group_member WHERE gid = #{gid} AND userid = #{userid}")
    GroupMember getGroupMember(@Param("gid") String gid, @Param("userid") String userid);
    
    @Select("SELECT * FROM group_member WHERE group_member_id = #{groupMemberId}")
    GroupMember getGroupMemberById(String groupMemberId);
    
    @Select("SELECT * FROM group_member WHERE gid = #{gid}")
    List<GroupMember> getGroupMembers(String gid);
    
    @Select("SELECT * FROM group_member WHERE gid = #{gid} AND role = #{role}")
    List<GroupMember> getGroupMembersByRole(@Param("gid") String gid, @Param("role") int role);
    
    @Select("SELECT * FROM group_member WHERE userid = #{userid}")
    List<GroupMember> getUserGroups(String userid);
    
    @Select("SELECT COUNT(*) FROM group_member WHERE gid = #{gid}")
    int getGroupMemberCount(String gid);
    
    @Insert("INSERT INTO group_member(group_member_id, gid, userid, role, group_nickname) " +
            "VALUES(#{groupMemberId}, #{gid}, #{userid}, #{role}, #{groupNickname})")
    int addGroupMember(GroupMember groupMember);
    
    @Update("UPDATE group_member SET role = #{role} WHERE gid = #{gid} AND userid = #{userid}")
    int updateMemberRole(@Param("gid") String gid, @Param("userid") String userid, @Param("role") int role);
    
    @Update("UPDATE group_member SET group_nickname = #{groupNickname} WHERE gid = #{gid} AND userid = #{userid}")
    int updateGroupNickname(@Param("gid") String gid, @Param("userid") String userid, @Param("groupNickname") String groupNickname);
    
    @Update("UPDATE group_member SET silence_until = #{silenceUntil} WHERE gid = #{gid} AND userid = #{userid}")
    int silenceMember(@Param("gid") String gid, @Param("userid") String userid, @Param("silenceUntil") Date silenceUntil);
    
    @Update("UPDATE group_member SET last_active_time = NOW() WHERE gid = #{gid} AND userid = #{userid}")
    int updateLastActiveTime(@Param("gid") String gid, @Param("userid") String userid);
    
    @Delete("DELETE FROM group_member WHERE gid = #{gid} AND userid = #{userid}")
    int removeGroupMember(@Param("gid") String gid, @Param("userid") String userid);
    
    @Delete("DELETE FROM group_member WHERE gid = #{gid}")
    int removeAllGroupMembers(String gid);
    
    @Select("SELECT gm.* FROM group_member gm " +
            "JOIN `group` g ON gm.gid = g.gid " +
            "WHERE gm.gid = #{gid} AND gm.role = 2")
    GroupMember getGroupOwner(String gid);
    
    @Select("SELECT * FROM group_member WHERE gid = #{gid} AND role = 1")
    List<GroupMember> getGroupAdmins(String gid);
    
    @Select("SELECT * FROM group_member WHERE gid = #{gid} ORDER BY role DESC LIMIT #{offset}, #{limit}")
    List<GroupMember> getGroupMembersWithPagination(@Param("gid") String gid, @Param("offset") int offset, @Param("limit") int limit);
    
    @Select("SELECT * FROM group_member WHERE gid = #{gid} AND silence_until > NOW()")
    List<GroupMember> getSilencedMembers(String gid);
    
    /**
     * 批量根据用户ID获取群成员
     * @param gid 群组ID
     * @param userIds 用户ID列表
     * @return 群成员列表
     */
    List<GroupMember> batchGetMembersByUserIds(@Param("gid") String gid, @Param("userIds") List<String> userIds);
    
    /**
     * 获取群成员及用户信息
     * @param gid 群组ID
     * @return 包含群成员和用户信息的映射列表
     */
    List<Map<String, Object>> getGroupMembersWithUserInfo(String gid);
}
