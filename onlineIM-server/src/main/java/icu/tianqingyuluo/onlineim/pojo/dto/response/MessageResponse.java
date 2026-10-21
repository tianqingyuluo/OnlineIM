package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 消息响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    /**
     * 消息ID
     */
    @JsonProperty("message_id")
    private String messageId;

    /**
     * 会话ID
     */
    @JsonProperty("conversation_id")
    private String conversationId;

    /**
     * 发送者信息
     */
    @JsonProperty("sender_info")
    private UserBriefResponse senderInfo;

    /**
     * 消息类型：text, image, file, audio, video
     */
    @JsonProperty("message_type")
    private String messageType;

    /**
     * 消息内容
     */
    private Object content;

    @JsonProperty("reply_to")
    private ReplyReferenceResponse replyTo;

    /**
     * 逐条转发的消息：原作者 {user_id, display_name}；普通消息为 null
     */
    @JsonProperty("forwarded_from")
    private ForwardedFrom forwardedFrom;

    /**
     * 是否被编辑过（文本消息）
     */
    private Boolean edited;

    /**
     * 最近一次编辑时间，格式同 timestamp；未编辑为 null
     */
    @JsonProperty("edited_at")
    private String editedAt;

    /**
     * 内容版本（从 1 开始，每次编辑 +1），客户端据此丢弃乱序到达的旧版本
     */
    @JsonProperty("content_revision")
    private Integer contentRevision;

    /**
     * 群聊 @提及的用户ID列表（服务端清洗后，只含发送时的群成员）
     */
    @JsonProperty("at_users")
    private List<String> mentionedUserIds;

    /**
     * 消息状态：sending, delivered, recalled
     */
    private String status;

    /**
     * 可靠投递状态：sending, failed, sent, delivered, read。
     * 与兼容保留的 status 数值/撤回语义分离。
     */
    @JsonProperty("delivery_state")
    private String deliveryState;

    /**
     * 是否已撤回
     */
    @JsonProperty("is_recalled")
    private Boolean isRecalled;

    /**
     * 消息序列id
     */
    @JsonProperty("seq_id")
    private String seqId;

    /**
     * 客户端发送的去重ID
     */
    @JsonProperty("client_message_id")
    private String clientMessageId;

    /**
     * 发送时间
     */
    private String timestamp;


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForwardedFrom {
        @JsonProperty("user_id")
        private String userId;
        @JsonProperty("display_name")
        private String displayName;
    }
}
