package icu.tianqingyuluo.onlineim.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群组设置实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSetting {
    private String id;                  // 设置ID，格式为：set_+UUID
    private String groupId;             // 群组ID，格式为：grp_+UUID
    private Integer allowMemberInvite;  // 是否允许成员邀请：0-禁止，1-允许
    private Integer allowMemberModifyName; // 是否允许成员修改群名片：0-禁止，1-允许
    private Integer allowMemberUploadFile; // 是否允许成员上传文件：0-禁止，1-允许
    private Integer allowMemberAtAll;   // 是否允许成员@全体成员：0-禁止，1-允许
    private Integer allowViewHistoryMessage; // 是否允许查看历史消息：0-禁止，1-允许
    private LocalDateTime createdAt;    // 创建时间
    private LocalDateTime updatedAt;    // 更新时间
} 