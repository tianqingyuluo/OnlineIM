package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 转发结果：每个目标一项，messages 为在该目标会话中新产生的消息（与历史接口格式相同）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageForwardResponse {

    private String mode;

    private List<TargetResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetResult {
        @JsonProperty("target_id")
        private String targetId;

        @JsonProperty("conversation_id")
        private String conversationId;

        private List<MessageResponse> messages;
    }
}
