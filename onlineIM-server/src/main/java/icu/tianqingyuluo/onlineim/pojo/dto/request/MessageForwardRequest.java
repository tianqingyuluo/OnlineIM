package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 转发请求：把来源会话中的若干条消息转发到一个或多个目标（好友 usr_ / 已加入的群 grp_）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageForwardRequest {

    /** 来源会话 ID（私聊 conv_ 或群 grp_） */
    @JsonProperty("source_conversation_id")
    @JsonAlias("sourceConversationId")
    private String sourceConversationId;

    /** 要转发的消息 ID（按 seq_id 排序后转发，重复项去掉） */
    @JsonProperty("message_ids")
    @JsonAlias("messageIds")
    private List<String> messageIds;

    /** 目标：好友 user_id 或群 group_id */
    @JsonProperty("targets")
    private List<String> targets;

    /** separate（逐条转发，默认）或 merged（合并成一条聊天记录） */
    @JsonProperty("mode")
    private String mode;

    /** 客户端幂等键：同一请求重试不会重复产生消息 */
    @JsonProperty("client_request_id")
    @JsonAlias("clientRequestId")
    private String clientRequestId;
}
