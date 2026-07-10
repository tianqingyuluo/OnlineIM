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
public class ReceiptEventPayload {
    @JsonProperty("receipt_type")
    private String receiptType;

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("seq_id")
    private String seqId;

    @JsonProperty("receiver_id")
    private String receiverId;

    @JsonProperty("reader_id")
    private String readerId;

    @JsonProperty("read_seq")
    private String readSeq;
}
