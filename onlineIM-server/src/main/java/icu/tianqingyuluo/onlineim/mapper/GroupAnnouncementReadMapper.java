package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.dto.response.AnnouncementReadCountRow;
import icu.tianqingyuluo.onlineim.pojo.dto.response.AnnouncementReaderResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupAnnouncementReadMapper {

    @Select("SELECT announcement_id FROM group_announcement_reads WHERE group_id = #{groupId} AND user_id = #{userId}")
    List<String> listReadAnnouncementIds(@Param("groupId") String groupId, @Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM group_announcement_reads WHERE announcement_id = #{announcementId} AND user_id = #{userId}")
    int countByUser(@Param("announcementId") String announcementId, @Param("userId") String userId);

    @Select("SELECT COUNT(*) FROM group_members WHERE group_id = #{groupId} AND status = 1")
    int countActiveMembers(@Param("groupId") String groupId);

    @Select("""
            SELECT r.announcement_id AS announcementId, COUNT(*) AS readCount
            FROM group_announcement_reads r
            JOIN group_members gm ON gm.group_id = r.group_id AND gm.user_id = r.user_id AND gm.status = 1
            WHERE r.group_id = #{groupId}
            GROUP BY r.announcement_id
            """)
    List<AnnouncementReadCountRow> countReads(@Param("groupId") String groupId);

    @Select("SELECT user_id FROM group_members WHERE group_id = #{groupId} AND status = 1")
    List<String> listActiveMemberIds(@Param("groupId") String groupId);

    @Select("""
            SELECT gm.user_id FROM group_members gm
            WHERE gm.group_id = #{groupId} AND gm.status = 1 AND gm.role <> 0
            UNION
            SELECT owner_id FROM `groups` WHERE id = #{groupId}
            """)
    List<String> listManagerIds(@Param("groupId") String groupId);

    @Insert("""
            INSERT INTO group_announcement_reads(id, announcement_id, group_id, user_id, read_at)
            VALUES(#{id}, #{announcementId}, #{groupId}, #{userId}, NOW())
            """)
    int insert(@Param("id") String id,
               @Param("announcementId") String announcementId,
               @Param("groupId") String groupId,
               @Param("userId") String userId);

    @Delete("DELETE FROM group_announcement_reads WHERE announcement_id = #{announcementId}")
    int deleteByAnnouncement(@Param("announcementId") String announcementId);

    @Select("""
            SELECT r.user_id AS userId, u.username AS username, u.nickname AS nickname,
                   u.avatar AS avatarUrl, DATE_FORMAT(r.read_at, '%Y-%m-%d %H:%i:%s') AS readAt
            FROM group_announcement_reads r
            JOIN group_members gm ON gm.group_id = r.group_id AND gm.user_id = r.user_id AND gm.status = 1
            JOIN users u ON u.id = r.user_id
            WHERE r.announcement_id = #{announcementId}
            ORDER BY r.read_at ASC
            """)
    List<AnnouncementReaderResponse> listReaders(@Param("announcementId") String announcementId);

    @Select("""
            SELECT gm.user_id AS userId, u.username AS username, u.nickname AS nickname, u.avatar AS avatarUrl
            FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            WHERE gm.group_id = #{groupId} AND gm.status = 1
              AND NOT EXISTS (
                  SELECT 1 FROM group_announcement_reads r
                  WHERE r.announcement_id = #{announcementId} AND r.user_id = gm.user_id
              )
            ORDER BY gm.role DESC, u.nickname
            """)
    List<AnnouncementReaderResponse> listUnreadMembers(@Param("groupId") String groupId,
                                                       @Param("announcementId") String announcementId);
}
