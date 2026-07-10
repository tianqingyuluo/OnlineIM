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
public class MessageAckPayload {
    @JsonProperty("client_message_id")
    private String clientMessageId;

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("seq_id")
    private String seqId;

    @JsonProperty("delivery_state")
    private String deliveryState;

    @JsonProperty("server_time")
    private long serverTime;
}
