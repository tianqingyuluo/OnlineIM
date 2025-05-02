package icu.tianqingyuluo.onlineim.pojo.dto.request;

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
    private Boolean allowMemberInvite;
    
    /**
     * 是否允许成员修改群名片
     */
    private Boolean allowMemberModifyName;
    
    /**
     * 是否允许成员上传文件
     */
    private Boolean allowMemberUploadFile;
    
    /**
     * 是否允许成员@全体成员
     */
    private Boolean allowMemberAtAll;
    
    /**
     * 是否允许查看历史消息
     */
    private Boolean allowViewHistoryMessage;
} 