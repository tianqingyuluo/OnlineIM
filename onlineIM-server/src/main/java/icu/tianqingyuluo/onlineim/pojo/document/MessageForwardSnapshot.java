package icu.tianqingyuluo.onlineim.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 逐条转发产生的消息上的溯源快照：记录原作者（转发链上最初的发送者）。
 *
 * <p>来源会话 / 原消息 ID 只在服务端保存用于排查，不在接口中返回，避免把私聊关系暴露给目标会话。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageForwardSnapshot {
    private String originalSenderId;
    private String originalSenderName;
    private String sourceConversationId;
    private String sourceMessageId;
    private String forwardedBy;
}
