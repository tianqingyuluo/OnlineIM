package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 会话成员的送达/已读游标。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_read_states")
@CompoundIndex(name = "conversation_user_unique", def = "{'conversationId': 1, 'userId': 1}", unique = true)
@CompoundIndex(name = "conversation_read_seq", def = "{'conversationId': 1, 'readSeq': 1}")
public class ConversationReadState {
    @Id
    private String id;                 // conversationId:userId
    @Indexed
    private String conversationId;
    @Indexed
    private String userId;
    private String conversationType;   // private, group
    private String deliveredSeq;       // 客户端已写入本地消息库的最新连续 seq
    private String readSeq;            // 客户端已阅读的最新连续 seq
    private Date updatedAt;

    @Version
    private Long version;
}
