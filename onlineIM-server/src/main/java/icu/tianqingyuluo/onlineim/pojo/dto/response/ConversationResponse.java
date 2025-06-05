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
} 