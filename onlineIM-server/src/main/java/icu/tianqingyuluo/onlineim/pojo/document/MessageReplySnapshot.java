package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送时生成的直接引用快照。不会递归保存被引用消息自身的引用关系。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReplySnapshot {
    private String messageId;
    private String seqId;
    private String senderId;
    private String senderDisplayName;
    private String messageType;
    private String previewText;
    private Integer contentRevision;
    private String state;
}
