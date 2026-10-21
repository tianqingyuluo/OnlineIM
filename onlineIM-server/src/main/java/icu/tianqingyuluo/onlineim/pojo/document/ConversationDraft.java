package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * 某个用户在某个会话里未发送的草稿。每个用户每个会话最多一份，后写覆盖。
 * {@code _id = userId + ":" + conversationId}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_drafts")
public class ConversationDraft {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String conversationId;

    /** 输入框原文（保留首尾空白）；全空白不会落库 */
    private String content;

    /** 回复引用快照；目标不可用时不存 */
    private MessageReplySnapshot replyTo;

    /** 群聊草稿里选中的 @ 成员（名字是输入时插入的显示名，只给草稿主人恢复输入框） */
    private List<Mention> atUsers;

    /** 服务端写入时间（毫秒） */
    private Long updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mention {
        private String userId;
        private String name;
    }

    public static String documentId(String userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
