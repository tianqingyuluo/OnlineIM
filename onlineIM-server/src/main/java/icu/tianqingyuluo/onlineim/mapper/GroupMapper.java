package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.Group;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 群组数据访问接口
 * 提供群组相关的数据库操作
 */
@Mapper
public interface GroupMapper {

    /**
     * 根据ID查询群组
     */
    @Select("SELECT * FROM groups WHERE id = #{id}")
    Group getById(String id);
    
    /**
     * 根据名称模糊搜索群组
     */
    @Select("SELECT * FROM groups WHERE name LIKE CONCAT('%', #{keyword}, '%') AND status = 1")
    List<Group> searchByName(String keyword);
    
    /**
     * 新增群组
     */
    @Insert("INSERT INTO groups(id, name, avatar, description, owner_id, max_members, current_members, " +
           "join_type, mute_type, status, created_at, updated_at) " +
           "VALUES(#{id}, #{name}, #{avatar}, #{description}, #{ownerId}, #{maxMembers}, #{currentMembers}, " +
           "#{joinType}, #{muteType}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(Group group);
    
    /**
     * 更新群组基本信息
     */
    @Update("UPDATE groups SET name = #{name}, avatar = #{avatar}, description = #{description}, " +
           "join_type = #{joinType}, mute_type = #{muteType}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(Group group);
    
    /**
     * 更新群组状态
     */
    @Update("UPDATE groups SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") Integer status, @Param("updatedAt") String updatedAt);
    
    /**
     * 更新群主
     */
    @Update("UPDATE groups SET owner_id = #{newOwnerId}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateOwner(@Param("id") String id, @Param("newOwnerId") String newOwnerId, @Param("updatedAt") String updatedAt);
    
    /**
     * 更新群组成员数量
     */
    @Update("UPDATE groups SET current_members = current_members + #{delta} WHERE id = #{id}")
    int updateMemberCount(@Param("id") String id, @Param("delta") int delta);
    
    /**
     * 更新群组加入方式
     */
    @Update("UPDATE groups SET join_type = #{joinType}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateJoinType(@Param("id") String id, @Param("joinType") Integer joinType, @Param("updatedAt") String updatedAt);
    
    /**
     * 更新群组禁言类型
     */
    @Update("UPDATE groups SET mute_type = #{muteType}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateMuteType(@Param("id") String id, @Param("muteType") Integer muteType, @Param("updatedAt") String updatedAt);
    
    /**
     * 获取用户加入的群组列表（XML实现连表查询）
     */
    List<Group> getUserJoinedGroups(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 获取用户加入的群组数量（XML实现连表查询）
     */
    int countUserJoinedGroups(String userId);
    
    /**
     * 根据群主ID获取群组列表
     */
    @Select("SELECT * FROM groups WHERE owner_id = #{ownerId} AND status = 1")
    List<Group> getGroupsByOwnerId(String ownerId);
    
    /**
     * 获取群组总数
     */
    @Select("SELECT COUNT(*) FROM groups WHERE status = 1")
    int getTotalGroupCount();
    
    /**
     * 分页获取所有群组
     */
    @Select("SELECT * FROM groups WHERE status = 1 ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Group> getGroupsWithPagination(@Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 查询群组以及成员数量（XML实现复杂查询）
     * @param groupIds 群组ID列表
     * @return 包含群组信息和成员数量的Map列表
     */
    List<Map<String, Object>> getGroupsWithMemberCount(@Param("groupIds") List<String> groupIds);
}
