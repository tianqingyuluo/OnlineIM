package icu.tianqingyuluo.onlineim.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceSettingsRequest {

    @NotNull(message = "请指定是否隐藏在线状态")
    @JsonProperty("hide_online_status")
    private Boolean hideOnlineStatus;
}
