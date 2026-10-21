package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 编辑文本消息：REST PUT /api/v1/messages/{message_id} 的请求体，也是 WebSocket EDIT_MESSAGE_REQUEST 的 message。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageEditRequest {

    /** 仅 WebSocket 使用；REST 以路径参数为准 */
    @JsonProperty("message_id")
    @JsonAlias("messageId")
    private String messageId;

    /** 新的文本内容 */
    @JsonProperty("content")
    private String content;

    /**
     * 群聊：编辑后的 @ 列表（会按当前成员重新清洗）；省略（null）表示保持原列表。私聊忽略。
     */
    @JsonProperty("at_users")
    @JsonAlias({"atUsers", "mentioned_user_ids"})
    private List<String> atUsers;
}
