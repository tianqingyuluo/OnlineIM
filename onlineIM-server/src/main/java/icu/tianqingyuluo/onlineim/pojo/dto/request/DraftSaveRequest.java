package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 保存会话草稿。content 为空或只有空白时服务端删除草稿。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftSaveRequest {

    private String content;

    @JsonProperty("reply_to_message_id")
    @JsonAlias("replyToMessageId")
    private String replyToMessageId;

    @JsonProperty("at_users")
    @JsonAlias("atUsers")
    private List<Mention> atUsers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mention {
        @JsonProperty("user_id")
        @JsonAlias("userId")
        private String userId;

        private String name;
    }
}
