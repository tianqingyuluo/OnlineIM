package icu.tianqingyuluo.onlineim.pojo.dto.request.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PrivateMessageRequest {

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("sender_id")
    private String senderId;

    @JsonProperty("receiver_id")
    private String receiverId;     // 接收者ID，格式为：usr_+UUID

    @JsonProperty("message_type")
    private String messageType;    // 消息类型：text, image, voice, file, etc.

    private String content;        // 文本内容或媒体文件引用

    @JsonProperty("client_message_id")
    private String clientMessageId;


    @JsonProperty("reply_to_message_id")
    private String replyToMessageId;

}
