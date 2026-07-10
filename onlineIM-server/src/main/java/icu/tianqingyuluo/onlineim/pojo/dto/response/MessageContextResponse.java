package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageContextResponse {
    @JsonProperty("target_message_id")
    private String targetMessageId;

    private List<MessageResponse> messages;

    @JsonProperty("has_more_before")
    private boolean hasMoreBefore;

    @JsonProperty("has_more_after")
    private boolean hasMoreAfter;
}
