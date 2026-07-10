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
public class MessageReadersResponse {
    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("seq_id")
    private String seqId;

    private List<UserBriefResponse> readers;

    /**
     * 已送达该消息的成员，供发送者离线重连后恢复“已送达”状态。
     */
    @JsonProperty("delivered_readers")
    private List<UserBriefResponse> deliveredReaders;
}
