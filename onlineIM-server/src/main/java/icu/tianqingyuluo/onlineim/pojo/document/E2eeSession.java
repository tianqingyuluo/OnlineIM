package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 私聊是否开启端到端加密。文档 _id 即会话 ID。群聊不使用此集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "e2ee_sessions")
public class E2eeSession {

    @Id
    private String conversationId;

    private Boolean enabled;

    /** 最后一次切换的用户 */
    private String updatedBy;

    private Date updatedAt;
}
