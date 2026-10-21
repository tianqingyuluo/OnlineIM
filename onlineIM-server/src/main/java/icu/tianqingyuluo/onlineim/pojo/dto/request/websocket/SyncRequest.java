package icu.tianqingyuluo.onlineim.pojo.dto.request.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户端在 WebSocket 连接或重连成功后上报各会话本地最大 seq，请求服务端补齐漏收。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncRequest {

    /** 各会话的本地游标；没出现的会话仍会在响应里带回未读数和最新预览 */
    private List<ConversationCursor> conversations;

    /** 当前打开的会话里已经加载的消息，用于补上这些消息在离线期间的编辑 / 撤回 */
    private List<WatchCursor> watch;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationCursor {
        @JsonProperty("conversation_id")
        private String conversationId;

        @JsonProperty("last_seq")
        private String lastSeq;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WatchCursor {
        @JsonProperty("conversation_id")
        private String conversationId;

        @JsonProperty("message_ids")
        private List<String> messageIds;
    }
}
