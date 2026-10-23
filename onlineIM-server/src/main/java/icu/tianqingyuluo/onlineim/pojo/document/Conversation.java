package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 会话列表文档类 (MongoDB)。
 *
 * <p>每个参与者各有一条记录（见 docs/conversation-model.md）：
 * {@code _id = conversationId + ":" + userId}，{@code (userId, conversationId)} 唯一。
 * 置顶、免打扰、未读缓存、删除（隐藏）状态都只属于 {@link #userId} 这一位所有者。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {
    @Id
    private String id;             // 记录ID：conversationId + ":" + userId
    private String conversationId; // 对外的会话ID：私聊 conv_+两个用户ID，群聊即群ID grp_xxx
    private String userId;         // 记录所有者（当前用户）ID
    private String targetId;       // 对端：单聊为对方用户ID，群聊为群组ID
    private String conversationType; // 会话类型：private(单聊), group(群聊)
    private Integer unreadCount;   // 所有者的未读数缓存（权威值由 conversation_read_states 计算）
    private LastMessage lastMessage; // 最后一条消息的摘要信息
    private Boolean top;           // 所有者是否置顶
    private Boolean mute;          // 所有者是否免打扰
    private Integer status;        // 所有者视角的会话状态：0-已删除（隐藏），1-正常
    private Date updatedAt;        // 更新时间，用于会话排序
    private java.util.List<PendingMention> pendingMentions; // 群聊中 @所有者 且尚未读到的消息（最多保留最近 20 条）
    
    /**
     * 未读的 @我 消息：读到（read_seq ≥ seqId）或消息被撤回后移除
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingMention {
        private String messageId;
        private String seqId;
        private String senderId;
        /** true 表示这条“有人@我”来自 @所有人（at_users 含 all） */
        private Boolean atAll;
        /** true 表示 at_users 里有这位成员自己的 ID（同一条消息也可以同时 @所有人） */
        private Boolean personal;
    }

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
        private String senderId;    // 发送者ID（用于预览“某某: ”与“你/某某 撤回了一条消息”，旧记录为空）
    }
} 