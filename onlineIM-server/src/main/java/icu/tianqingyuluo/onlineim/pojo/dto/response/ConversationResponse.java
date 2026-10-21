package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    /**
     * 会话ID
     */
    @JsonProperty("conversation_id")
    private String conversationID;

    /**
     * 会话类型：private, group
     */
    private String type;

    /**
     * 目标信息（用户或群组）
     */
    @JsonProperty("target_info")
    private TargetInfoResponse targetInfo;

    /**
     * 最新一条消息
     */
    @JsonProperty("last_message")
    private MessagePreviewResponse lastMessage;

    /**
     * 未读消息数
     */
    @JsonProperty("unread_count")
    private Integer unreadCount;

    /**
     * 是否有未读的 @我（群聊）；客户端在会话列表显示“[有人@我]”
     */
    @JsonProperty("at_me")
    private Boolean atMe;

    /**
     * 未读的 @我 条数（最多统计最近 20 条）
     */
    @JsonProperty("at_me_count")
    private Integer atMeCount;

    /**
     * 最早一条未读 @我 的消息 ID，可用于跳转定位
     */
    @JsonProperty("at_me_message_id")
    private String atMeMessageId;

    /**
     * 是否有未读的 @所有人。为 true 时客户端优先显示“[@所有人]”；at_me 同时为 true，旧客户端仍显示“[有人@我]”。
     */
    @JsonProperty("at_all")
    private Boolean atAll;

    /** 未读的 @所有人 条数 */
    @JsonProperty("at_all_count")
    private Integer atAllCount;

    /** 最早一条未读 @所有人 的消息 ID */
    @JsonProperty("at_all_message_id")
    private String atAllMessageId;

    /**
     * 当前用户已送达的最新消息序列号
     */
    @JsonProperty("delivered_seq")
    private String deliveredSeq;

    /**
     * 当前用户已读的最新消息序列号
     */
    @JsonProperty("read_seq")
    private String readSeq;

    /**
     * 会话中的最新消息序列号
     */
    @JsonProperty("latest_seq")
    private String latestSeq;

    /**
     * 是否免打扰
     */
    @JsonProperty("is_muted")
    private Boolean isMuted;

    /**
     * 是否置顶
     */
    @JsonProperty("is_pinned")
    private Boolean isPinned;

    /**
     * 最后活动时间
     */
    @JsonProperty("last_activity_time")
    private String lastActivityTime;

    /**
     * 当前用户在该会话的未发送草稿摘要（折叠空白、最多 40 个字符）。没有草稿时为 null。
     * 客户端在会话列表显示“[草稿] ”加这段文字。
     */
    @JsonProperty("draft_preview")
    private String draftPreview;
}
