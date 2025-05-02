package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 用户消息时间线文档类 (MongoDB)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_timelines")
public class UserTimeline {
    @Id
    private String id;              // MongoDB自动生成的ID
    private String userId;          // 用户ID，格式为：usr_+UUID
    private String messageId;       // 消息ID，格式为：msg_+UUID，引用单聊或群聊消息集合
    private String messageType;     // 消息类型：private(单聊), group(群聊), system(系统通知)
    private String conversationId;  // 会话ID，格式为：conv_+UUID或grp_+UUID
    private Date timestamp;         // 消息时间戳，用于排序和增量同步
    private Integer status;         // 消息状态：0-未读，1-已读，2-已删除，3-已撤回
    private String deviceId;        // 消息产生或状态更新的设备ID，用于多端同步
    private Long seqId;             // 序列号，用于消息排序和增量同步
    private Date createdAt;         // 创建时间
    private Date updatedAt;         // 更新时间
} 