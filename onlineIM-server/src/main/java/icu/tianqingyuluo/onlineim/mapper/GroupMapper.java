package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.Group;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupMapper {
    @Select("SELECT * FROM `group` WHERE gid = #{gid}")
    Group getGroupById(String gid);
    
    @Select("SELECT * FROM `group` WHERE ownerid = #{ownerid}")
    List<Group> getGroupsByOwnerId(String ownerid);
    
    @Select("SELECT * FROM `group` WHERE gname LIKE CONCAT('%', #{keyword}, '%')")
    List<Group> searchGroupsByName(String keyword);
    
    @Insert("INSERT INTO `group`(gid, gname, gavatar, ownerid) VALUES(#{gid}, #{gname}, #{gavatar}, #{ownerid})")
    int createGroup(Group group);
    
    @Update("UPDATE `group` SET gname = #{gname} WHERE gid = #{gid}")
    int updateGroupName(@Param("gid") String gid, @Param("gname") String gname);
    
    @Update("UPDATE `group` SET gavatar = #{gavatar} WHERE gid = #{gid}")
    int updateGroupAvatar(@Param("gid") String gid, @Param("gavatar") String gavatar);
    
    @Update("UPDATE `group` SET ownerid = #{newOwnerId} WHERE gid = #{gid}")
    int transferGroupOwnership(@Param("gid") String gid, @Param("newOwnerId") String newOwnerId);
    
    @Delete("DELETE FROM `group` WHERE gid = #{gid}")
    int deleteGroup(String gid);
    
    @Select("SELECT COUNT(*) FROM `group`")
    int getTotalGroupCount();
    
    @Select("SELECT g.* FROM `group` g " +
            "JOIN group_member gm ON g.gid = gm.gid " +
            "WHERE gm.userid = #{userId}")
    List<Group> getGroupsByUserId(String userId);
    
    @Select("SELECT g.* FROM `group` g " +
            "JOIN group_member gm ON g.gid = gm.gid " +
            "WHERE gm.userid = #{userId} " +
            "ORDER BY gm.last_active_time DESC LIMIT #{limit}")
    List<Group> getRecentActiveGroupsByUserId(@Param("userId") String userId, @Param("limit") int limit);
    
    /**
     * 查询群组以及成员数量
     * @param groupIds 群组ID列表
     * @return 包含群组信息和成员数量的Map列表
     */
    List<Map<String, Object>> getGroupsWithMemberCount(@Param("groupIds") List<String> groupIds);
}
