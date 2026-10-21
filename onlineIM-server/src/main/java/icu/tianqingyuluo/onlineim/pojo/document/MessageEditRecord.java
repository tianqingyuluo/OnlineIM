package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 文本消息编辑前的一个版本（只在服务端保存，用于排查与以后的“查看原内容”，接口不返回）。
 * editHistory[0] 始终是最初发送的原文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEditRecord {
    private Integer revision;      // 该版本的 contentRevision
    private String content;        // 该版本的内容
    private List<String> atUsers;  // 该版本的 @ 列表（群聊）
    private Date replacedAt;       // 被新版本替换的时间
}
