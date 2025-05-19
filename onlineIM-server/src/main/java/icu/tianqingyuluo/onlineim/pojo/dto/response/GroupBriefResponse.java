package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupBriefResponse {
    @JsonProperty("group_id")
    private String groupID;

    private String name;

    @JsonProperty("avatar_url")
    private String avatar;

    private String description;

    @JsonProperty("owner_id")
    private String ownerID;

    @JsonProperty("join_type")
    private String joinType;
}
