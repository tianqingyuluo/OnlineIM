package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConversationCreateRequest {

    @NotBlank(message = "目标ID不能为空")
    @JsonProperty("target_id")
    private String targetId; // 如果是私聊就是对应的好友userid，如果是群组就是群组id

    @NotBlank(message = "会话类型不能为空")
    private String type; // group、private 或 user

}
