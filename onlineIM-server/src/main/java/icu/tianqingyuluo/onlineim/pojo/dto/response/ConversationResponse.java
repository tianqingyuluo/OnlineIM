package icu.tianqingyuluo.onlineim.pojo.dto.response;

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
    private String conversationId;
    
    /**
     * 会话类型：private, group
     */
    private String type;
    
    /**
     * 目标信息（用户或群组）
     */
    private TargetInfoResponse targetInfo;
    
    /**
     * 最新一条消息
     */
    private MessagePreviewResponse lastMessage;
    
    /**
     * 未读消息数
     */
    private Integer unreadCount;
    
    /**
     * 是否免打扰
     */
    private Boolean isMuted;
    
    /**
     * 是否置顶
     */
    private Boolean isPinned;
    
    /**
     * 最后活动时间
     */
    private String lastActivityTime;
} 