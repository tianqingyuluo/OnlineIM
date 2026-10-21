package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/v1/messages/search 的响应：按时间倒序的命中消息，游标分页。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSearchResponse {
    /** 实际用于检索的关键字（已去掉首尾空白） */
    private String keyword;

    /** 只搜某个会话时为该会话 ID，全局搜索为 null */
    @JsonProperty("conversation_id")
    private String conversationId;

    private List<Item> results;

    /** 下一页游标，没有更多时为 null */
    @JsonProperty("next_cursor")
    private String nextCursor;

    @JsonProperty("has_more")
    private boolean hasMore;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private MessageResponse message;
        private ConversationInfo conversation;
        /** 命中片段（换行替换为空格，过长时首尾带“…”） */
        private String snippet;
        /** 片段内的命中位置（UTF-16 下标） */
        private List<Highlight> highlights;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationInfo {
        @JsonProperty("conversation_id")
        private String conversationId;
        /** private / group */
        private String type;
        /** 私聊为对方的备注或昵称，群聊为群名 */
        private String name;
        @JsonProperty("avatar_url")
        private String avatarUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Highlight {
        private int start;
        private int length;
    }
}
