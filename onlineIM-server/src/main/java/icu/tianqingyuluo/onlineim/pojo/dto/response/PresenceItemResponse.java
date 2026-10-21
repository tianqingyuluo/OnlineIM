package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceItemResponse {

    @JsonProperty("user_id")
    private String userId;

    /** online / offline。visible=false 时不返回。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String status;

    /** 离线时的上次在线时间（epoch millis）。在线、隐藏或不可见时不返回。 */
    @JsonProperty("last_seen_at")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long lastSeenAt;

    /** false 表示调用者无权查看，此时没有 status / last_seen_at。 */
    private boolean visible;
}
