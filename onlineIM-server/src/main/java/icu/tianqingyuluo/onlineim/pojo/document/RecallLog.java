package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 消息撤回日志文档类 (MongoDB)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "recall_logs")
public class RecallLog {
    @Id
    private String id;             // 记录ID
    private String conversationId; // 会话ID，可以是单聊会话ID或群聊ID
    private String seqId;            // 被撤回消息的序列号
    private String messageId;      // 被撤回消息的ID
    private String operatorId;     // 执行撤回操作的用户ID
    private Date recallTime;       // 撤回时间
}
