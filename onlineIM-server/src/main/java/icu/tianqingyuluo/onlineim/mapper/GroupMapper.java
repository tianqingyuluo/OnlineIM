package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupCreateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.request.GroupUpdateRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.GroupBriefResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.Group;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;

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
    @Select("SELECT id AS groupID, name, avatar, description, owner_id AS ownerID, join_type AS joinType FROM `groups` WHERE id = #{id}")
    GroupBriefResponse getById(String id);

    /**
     * 根据名称模糊搜索群组
     */
    @Select("SELECT * FROM `groups` WHERE name LIKE CONCAT('%', #{keyword}, '%') AND status = 1")
    List<Group> searchByName(String keyword);

    /**
     * 判断用户是否为群主
     */
    @Select("SELECT COUNT(*) > 0 FROM `groups` WHERE `groups`.id = #{groupId} AND `groups`.owner_id = #{userId}")
    boolean isGroupOwner(@Param("groupId") String groupId, @Param("userId") String userId);

    /**
     * 检查用户是否是群成员，并有权查看成员列表
     */
    @Select("SELECT COUNT(*) > 0 FROM group_members WHERE group_id = #{groupId} AND user_id = #{userid}")
    boolean isUserMember(@Param("groupId") String groupId, @Param("userid") String userid);

    /**
     * 检查加入群组是否无需验证
     */
    @Select("SELECT IF( join_type= 1 AND join_type!=2, true, false) FROM `groups` WHERE id=#{groupId}")
    boolean isNoVerificationToJoin(String groupId);

    /**
     * 检查是否允许主动加入群组
     */
    @Select("SELECT IF( join_type!=2, true, false) FROM `groups` WHERE id=#{groupId}")
    boolean isAllowToJoin(String groupId);
    /**
     * 查询群组id是否存在
     */
    @Select("SELECT EXISTS(SELECT 1 FROM `groups` WHERE id = #{groupId})")
    boolean existsByGroupId(String groupId);

    /**
     * 新增群组
     */
    @Insert("INSERT INTO `groups`(id, name, avatar, description, owner_id, max_members ,current_members) " +
            "VALUES(#{id}, #{groupCreateRequest.name}, #{groupCreateRequest.avatar}, #{groupCreateRequest.description},#{userid}, #{groupCreateRequest.maxMembers}, #{current_members})")
    void insertGroup(@Param("id") String id,@Param("userid") String userid, @Param("groupCreateRequest") GroupCreateRequest groupCreateRequest,@Param("current_members") int current_members);

    /**
     * 新增群组成员
     */
    void insertGroupMember(@Param("gid") String gid,@Param("memberIDs") List<String> memberIDs);
    @Lang(XMLLanguageDriver.class)  // 关键注解

    void batchInsertMembers(
            @Param("groupId") Long groupId,
            @Param("memberIds") List<String> memberIds
    );

    /**
     * 判断是否有管理员权限
     */
    @Select("SELECT IF( role!=0, true, false) FROM group_members WHERE group_id=#{groupId} AND user_id=#{userid}")
    boolean isAdmin(@Param("groupId") String groupId,@Param("userid") String userid);
    /**
     * 更新群组基本信息
     */
    @Update("UPDATE `groups` SET name = #{groupUpdateRequest.name}, avatar = #{groupUpdateRequest.avatar}, description = #{groupUpdateRequest.description}, " +
           "join_type = #{groupUpdateRequest.joinType} WHERE id = #{id}")
    int update(@Param("id") String id,@Param("groupUpdateRequest") GroupUpdateRequest groupUpdateRequest);

    /**
     * 更新群组状态
     */
    @Update("UPDATE `groups` SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") Integer status, @Param("updatedAt") String updatedAt);

    /**
     * 更新群主
     */
    @Update("UPDATE `groups` SET owner_id = #{newOwnerId}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateOwner(@Param("id") String id, @Param("newOwnerId") String newOwnerId, @Param("updatedAt") String updatedAt);

    /**
     * 更新群组成员数量
     */
    @Update("UPDATE `groups` SET current_members = current_members + #{delta} WHERE id = #{id}")
    int updateMemberCount(@Param("id") String id, @Param("delta") int delta);

    /**
     * 更新群组加入方式
     */
    @Update("UPDATE `groups` SET join_type = #{joinType}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateJoinType(@Param("id") String id, @Param("joinType") Integer joinType, @Param("updatedAt") String updatedAt);

    /**
     * 更新群组禁言类型
     */
    @Update("UPDATE `group_settings` SET mute_type = #{muteType} WHERE id = #{id}")
    int updateMuteType(@Param("id") String id, @Param("muteType") Integer muteType, @Param("updatedAt") String updatedAt);

    /**
     *  删除群组及相关信息
     */
    @Delete("DELETE FROM group_announcements WHERE group_id = #{groupId}")
    void deleteAnnouncements(@Param("groupId") String groupId);

    @Delete("DELETE FROM group_members WHERE group_id = #{groupId}")
    void deleteMembers(@Param("groupId") String groupId);

    @Delete("DELETE FROM `groups` WHERE id = #{groupId}")
    void deleteGroup(@Param("groupId") String groupId);
    /**
     * 获取用户加入的群组列表（XML实现连表查询）
     */
    List<Group> getUserJoinedGroups(@Param("userid") String userid);

    /**
     * 获取用户加入的群组数量（XML实现连表查询）
     */
    int countUserJoinedGroups(String userId);

    /**
     * 根据群主ID获取群组列表
     */
    @Select("SELECT * FROM `groups` WHERE owner_id = #{ownerId} AND status = 1")
    List<Group> getGroupsByOwnerId(String ownerId);

    /**
     * 获取群组总数
     */
    @Select("SELECT COUNT(*) FROM `groups` WHERE status = 1")
    int getTotalGroupCount();
    
    /**
     * 分页获取所有群组
     */
    @Select("SELECT * FROM `groups` WHERE status = 1 ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Group> getGroupsWithPagination(@Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 查询群组以及成员数量（XML实现复杂查询）
     * @param groupIds 群组ID列表
     * @return 包含群组信息和成员数量的Map列表
     */
    List<Map<String, Object>> getGroupsWithMemberCount(@Param("groupIds") List<String> groupIds);
}
