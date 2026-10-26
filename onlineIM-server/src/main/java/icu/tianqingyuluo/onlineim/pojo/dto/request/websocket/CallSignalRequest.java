package icu.tianqingyuluo.onlineim.pojo.dto.request.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 通话信令载荷。身份以 WebSocket 会话为准，不读取客户端自报的发起人。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallSignalRequest {

    @JsonProperty("call_id")
    private String callId;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("call_type")
    private String callType;

    private String sdp;

    private String candidate;

    @JsonProperty("sdp_mid")
    private String sdpMid;

    @JsonProperty("sdp_m_line_index")
    private Integer sdpMLineIndex;

    @JsonProperty("duration_seconds")
    private Integer durationSeconds;

    private String reason;
}
