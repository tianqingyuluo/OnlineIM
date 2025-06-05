package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群组设置更新请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSettingUpdateRequest {
    
    /**
     * 是否允许成员邀请
     */
    @JsonProperty("allow_member_invite")
    private Boolean allowMemberInvite;
    
    /**
     * 是否允许成员修改群名片
     */
    @JsonProperty("allow_member_modify_name")
    private Boolean allowMemberModifyName;
    
    /**
     * 是否允许成员上传文件
     */
    @JsonProperty("allow_member_upload_file")
    private Boolean allowMemberUploadFile;
    
    /**
     * 是否允许成员@全体成员
     */
    @JsonProperty("allow_member_at_all")
    private Boolean allowMemberAtAll;
    
    /**
     * 是否允许查看历史消息
     */
    @JsonProperty("allow_view_history_message")
    private Boolean allowViewHistoryMessage;
} 