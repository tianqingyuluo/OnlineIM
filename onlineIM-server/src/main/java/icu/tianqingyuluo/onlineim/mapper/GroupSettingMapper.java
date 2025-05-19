package icu.tianqingyuluo.onlineim.mapper;

import icu.tianqingyuluo.onlineim.pojo.entity.GroupSetting;
import org.apache.ibatis.annotations.*;

/**
 * 群组设置数据访问接口
 */
@Mapper
public interface GroupSettingMapper {

    @Select("SELECT * FROM group_settings WHERE id = #{id}")
    GroupSetting getById(String id);
    
    @Select("SELECT * FROM group_settings WHERE group_id = #{groupId}")
    GroupSetting getByGroupId(String groupId);

    @Select("SELECT IF(allow_member_invite = 1, true, false) FROM group_settings WHERE group_id=#{groupId}")
    boolean isAllowMemberInvite(String groupId);
    
    @Insert("INSERT INTO group_settings(id, group_id, allow_member_invite, allow_member_modify_name, " +
            "allow_member_upload_file, allow_member_at_all, allow_view_history_message, created_at, updated_at) " +
            "VALUES(#{id}, #{groupId}, #{allowMemberInvite}, #{allowMemberModifyName}, " +
            "#{allowMemberUploadFile}, #{allowMemberAtAll}, #{allowViewHistoryMessage}, #{createdAt}, #{updatedAt})")
    int insert(GroupSetting groupSetting);

    @Insert("INSERT INTO group_settings(id, group_id) " +
            "VALUES(#{id}, #{groupId})")
    int initSettings(@Param("id") String id,@Param("groupId") String groupId);
    
    @Update("UPDATE group_settings SET allow_member_invite = #{allowMemberInvite}, " +
            "allow_member_modify_name = #{allowMemberModifyName}, allow_member_upload_file = #{allowMemberUploadFile}, " +
            "allow_member_at_all = #{allowMemberAtAll}, allow_view_history_message = #{allowViewHistoryMessage}, " +
            "updated_at = #{updatedAt} WHERE id = #{id}")
    int update(GroupSetting groupSetting);
    
    @Delete("DELETE FROM group_settings WHERE id = #{id}")
    int delete(String id);
} 