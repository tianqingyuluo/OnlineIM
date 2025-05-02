package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 会话列表文档类 (MongoDB)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {
    @Id
    private String id;             // 会话ID，格式为：conv_+UUID或grp_+UUID
    private String userId;         // 用户ID，格式为：usr_+UUID
    private String targetId;       // 目标ID，单聊为用户ID(usr_+UUID)，群聊为群组ID(grp_+UUID)
    private String conversationType; // 会话类型：private(单聊), group(群聊)
    private Integer unreadCount;   // 未读消息数
    private LastMessage lastMessage; // 最后一条消息的摘要信息
    private Boolean top;           // 是否置顶
    private Boolean mute;          // 是否免打扰
    private Integer status;        // 会话状态：0-已删除，1-正常
    private Date updatedAt;        // 更新时间，用于会话排序
    
    /**
     * 最后一条消息摘要信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastMessage {
        private String messageId;   // 消息ID
        private String content;     // 消息内容或摘要
        private String messageType; // 消息类型
        private Date timestamp;     // 时间戳
    }
} 