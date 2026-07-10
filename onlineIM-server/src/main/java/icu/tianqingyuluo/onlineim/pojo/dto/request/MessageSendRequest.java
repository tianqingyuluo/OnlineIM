package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 发送消息请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {

    /**
     * 接收方ID，可以是用户ID或群组ID
     */
    @NotBlank(message = "接收方ID不能为空")
    @JsonProperty("target_id")
    @JsonAlias("targetId")
    private String targetId;

    /**
     * 消息类型：text, image, voice, video, file, location, etc.
     */
    @NotBlank(message = "消息类型不能为空")
    @JsonProperty("message_type")
    @JsonAlias("messageType")
    private String messageType;

    /**
     * 消息内容
     */
    @NotNull(message = "消息内容不能为空")
    private String content;

    /**
     * 引用的消息ID
     */
    @JsonProperty("reply_to_message_id")
    @JsonAlias("replyToMessageId")
    private String replyToMessageId;

    /**
     * 客户端消息ID，用于消息去重
     */
    @NotBlank(message = "客户端消息ID不能为空")
    @JsonProperty("client_message_id")
    @JsonAlias({"clientMessageId", "clientMsgId"})
    private String clientMsgId;

    /**
     * 提及的用户ID列表
     */
    @JsonProperty("at_user_ids")
    @JsonAlias({"atUserIds", "at_users"})
    private List<String> atUserIds;

    /**
     * 扩展字段，用于存储自定义属性
     */
    private Map<String, Object> ext;
}
