package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.GroupAnnouncement;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 群组公告数据访问接口
 * 提供群组公告相关的数据库操作
 */
@Mapper
public interface GroupAnnouncementMapper {
        
    @Select("SELECT * FROM group_announcement WHERE group_announcement_id = #{announcementId}")
    GroupAnnouncement getAnnouncementById(String announcementId);
    
    @Select("SELECT * FROM group_announcement WHERE gid = #{gid} AND status = 1 ORDER BY is_pinned DESC, create_time DESC")
    List<GroupAnnouncement> getAnnouncementsByGroupId(String gid);
    
    @Select("SELECT * FROM group_announcement WHERE gid = #{gid} AND is_pinned = 1 AND status = 1 ORDER BY create_time DESC")
    List<GroupAnnouncement> getPinnedAnnouncements(String gid);
    
    @Select("SELECT * FROM group_announcement WHERE gid = #{gid} AND status = 1 ORDER BY create_time DESC LIMIT 1")
    GroupAnnouncement getLatestAnnouncement(String gid);
    
    @Select("SELECT * FROM group_announcement WHERE gid = #{gid} AND status = 1 ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<GroupAnnouncement> getAnnouncementsWithPagination(@Param("gid") String gid, @Param("offset") int offset, @Param("limit") int limit);
    
    @Insert("INSERT INTO group_announcement(group_announcement_id, gid, userid, title, content, is_pinned) " +
            "VALUES(#{groupAnnouncementId}, #{gid}, #{userid}, #{title}, #{content}, #{isPinned})")
    int createAnnouncement(GroupAnnouncement announcement);
    
    @Update("UPDATE group_announcement SET status = 0 WHERE group_announcement_id = #{announcementId}")
    int deleteAnnouncement(String announcementId);
    
    @Update("UPDATE group_announcement SET title = #{title}, content = #{content} WHERE group_announcement_id = #{groupAnnouncementId}")
    int updateAnnouncement(GroupAnnouncement announcement);
    
    @Update("UPDATE group_announcement SET is_pinned = #{isPinned} WHERE group_announcement_id = #{announcementId}")
    int updateAnnouncementPinStatus(@Param("announcementId") String announcementId, @Param("isPinned") boolean isPinned);
    
    @Select("SELECT COUNT(*) FROM group_announcement WHERE gid = #{gid} AND status = 1")
    int getAnnouncementCount(String gid);
    
    @Update("UPDATE group_announcement SET is_pinned = 0 WHERE gid = #{gid} AND is_pinned = 1 AND group_announcement_id != #{exceptAnnouncementId}")
    int unpinOtherAnnouncements(@Param("gid") String gid, @Param("exceptAnnouncementId") String exceptAnnouncementId);
    
    @Select("SELECT * FROM group_announcement WHERE gid = #{gid} AND userid = #{userid} AND status = 1 ORDER BY create_time DESC")
    List<GroupAnnouncement> getAnnouncementsByUser(@Param("gid") String gid, @Param("userid") String userid);
    
    @Select("SELECT * FROM group_announcement WHERE gid = #{gid} AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%')) AND status = 1")
    List<GroupAnnouncement> searchAnnouncements(@Param("gid") String gid, @Param("keyword") String keyword);
    
    /**
     * 获取公告与发布者信息
     * @param gid 群组ID
     * @return 包含公告和发布者信息的映射列表
     */
    List<Map<String, Object>> getAnnouncementsWithPublisherInfo(String gid);
    
    /**
     * 根据动态条件查询公告
     * @param gid 群组ID（可选）
     * @param status 状态（可选）
     * @param isPinned 是否置顶（可选）
     * @param beginTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 符合条件的公告列表
     */
    List<GroupAnnouncement> getAnnouncementsWithCondition(
            @Param("gid") String gid,
            @Param("status") Integer status,
            @Param("isPinned") Boolean isPinned,
            @Param("beginTime") Date beginTime,
            @Param("endTime") Date endTime
    );
}
