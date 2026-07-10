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
public class ReadStateResponse {
    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("delivered_seq")
    private String deliveredSeq;

    @JsonProperty("read_seq")
    private String readSeq;

    @JsonProperty("latest_seq")
    private String latestSeq;

    @JsonProperty("unread_count")
    private long unreadCount;
}
