package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 草稿。cleared=true 只出现在 WebSocket {@code DRAFT_UPDATED}，表示该会话草稿已删除。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftResponse {

    @JsonProperty("conversation_id")
    private String conversationId;

    private String content;

    @JsonProperty("draft_preview")
    private String draftPreview;

    @JsonProperty("reply_to_message_id")
    private String replyToMessageId;

    @JsonProperty("reply_to")
    private ReplyReferenceResponse replyTo;

    @JsonProperty("at_users")
    private List<DraftMentionResponse> atUsers;

    @JsonProperty("updated_at")
    private Long updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean cleared;
}
