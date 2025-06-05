package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConversationCreateRequest {

    @JsonProperty("target_id")
    private String targetId; // 如果是私聊就是对应的好友userid，如果是群组就是群组id

    private String type; // group 或者 user

}
