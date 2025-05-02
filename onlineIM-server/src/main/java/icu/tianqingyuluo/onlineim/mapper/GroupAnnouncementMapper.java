package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.GroupAnnouncement;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 群组公告数据访问接口
 * 提供群组公告相关的数据库操作
 */
@Mapper
public interface GroupAnnouncementMapper {
        
    /**
     * 根据ID查询公告
     */
    @Select("SELECT * FROM group_announcements WHERE id = #{id}")
    GroupAnnouncement getById(String id);
    
    /**
     * 获取群组的公告列表（XML实现带分页和排序）
     */
    List<GroupAnnouncement> getByGroupId(@Param("groupId") String groupId, @Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 统计群组的公告数量
     */
    @Select("SELECT COUNT(*) FROM group_announcements WHERE group_id = #{groupId} AND status = 1")
    int countByGroupId(String groupId);
    
    /**
     * 新增群公告
     */
    @Insert("INSERT INTO group_announcements(id, group_id, publisher_id, title, content, status, pin, created_at, updated_at) " +
            "VALUES(#{id}, #{groupId}, #{publisherId}, #{title}, #{content}, #{status}, #{pin}, #{createdAt}, #{updatedAt})")
    int insert(GroupAnnouncement announcement);
    
    /**
     * 更新群公告内容
     */
    @Update("UPDATE group_announcements SET title = #{title}, content = #{content}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(GroupAnnouncement announcement);
    
    /**
     * 更新公告状态
     */
    @Update("UPDATE group_announcements SET status = #{status}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") int status, @Param("updatedAt") String updatedAt);
    
    /**
     * 更新公告置顶状态
     */
    @Update("UPDATE group_announcements SET pin = #{pin}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updatePin(@Param("id") String id, @Param("pin") int pin, @Param("updatedAt") String updatedAt);
    
    /**
     * 删除公告
     */
    @Delete("DELETE FROM group_announcements WHERE id = #{id}")
    int delete(String id);
    
    /**
     * 获取置顶公告
     */
    @Select("SELECT * FROM group_announcements WHERE group_id = #{groupId} AND pin = 1 AND status = 1 ORDER BY created_at DESC")
    List<GroupAnnouncement> getPinnedAnnouncements(@Param("groupId") String groupId);
    
    /**
     * 获取最新公告
     */
    @Select("SELECT * FROM group_announcements WHERE group_id = #{groupId} AND status = 1 ORDER BY created_at DESC LIMIT 1")
    GroupAnnouncement getLatestAnnouncement(@Param("groupId") String groupId);
    
    /**
     * 搜索公告（XML实现复杂LIKE查询）
     */
    List<GroupAnnouncement> searchAnnouncements(@Param("groupId") String groupId, @Param("keyword") String keyword);
    
    /**
     * 获取公告与发布者信息（XML实现连表查询）
     */
    List<Map<String, Object>> getAnnouncementsWithPublisherInfo(@Param("groupId") String groupId);
    
    /**
     * 根据动态条件查询公告（XML实现动态SQL）
     */
    List<GroupAnnouncement> getAnnouncementsWithCondition(
            @Param("groupId") String groupId,
            @Param("status") Integer status,
            @Param("pin") Boolean pin,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 将除指定公告外的其他公告取消置顶
     */
    @Update("UPDATE group_announcements SET pin = 0, updated_at = NOW() WHERE group_id = #{groupId} AND pin = 1 AND id != #{exceptId}")
    int unpinOtherAnnouncements(@Param("groupId") String groupId, @Param("exceptId") String exceptId);
}
