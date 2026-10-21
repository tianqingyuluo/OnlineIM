package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公告已读/未读名单中的一位成员。未读时没有 read_at。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementReaderResponse {

    @JsonProperty("user_id")
    private String userId;

    private String username;

    private String nickname;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("read_at")
    private String readAt;
}
