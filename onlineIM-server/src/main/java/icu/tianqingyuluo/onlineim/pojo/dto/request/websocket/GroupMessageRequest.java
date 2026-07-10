package icu.tianqingyuluo.onlineim.pojo.dto.request.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupMessageRequest {

    @JsonProperty("group_id")
    private String groupId;

    @JsonProperty("sender_id")
    private String senderId;

    @JsonProperty("at_users")
    private List<String> atUsers;

    @JsonProperty("message_type")
    private String messageType;

    private String content;

    @JsonProperty("client_message_id")
    private String clientMessageId;


    @JsonProperty("reply_to_message_id")
    private String replyToMessageId;

}
