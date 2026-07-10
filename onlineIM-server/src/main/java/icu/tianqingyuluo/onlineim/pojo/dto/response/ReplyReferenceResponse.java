package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyReferenceResponse {
    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("seq_id")
    private String seqId;

    @JsonProperty("sender_id")
    private String senderId;

    @JsonProperty("sender_display_name")
    private String senderDisplayName;

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("preview_text")
    private String previewText;

    private String state;
}
