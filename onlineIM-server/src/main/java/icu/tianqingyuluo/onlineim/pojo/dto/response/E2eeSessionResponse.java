package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class E2eeSessionResponse {

    @JsonProperty("conversation_id")
    private String conversationId;

    private Boolean enabled;

    @JsonProperty("updated_by")
    private String updatedBy;

    @JsonProperty("updated_at")
    private Long updatedAt;
}
