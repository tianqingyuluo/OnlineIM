package icu.tianqingyuluo.onlineim.pojo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前账号已登录的一台设备。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSessionResponse {

    @JsonProperty("device_id")
    private String deviceId;

    /** web / desktop / phone */
    @JsonProperty("device_type")
    private String deviceType;

    /** 列表上的可读名称。没有单独保存时由类型生成，例如「手机端」 */
    @JsonProperty("device_name")
    private String deviceName;

    /** WebSocket 是否还连着 */
    private boolean online;

    /** 是否就是发起查询的这台设备 */
    private boolean current;

    @JsonProperty("login_at")
    private Long loginAt;

    @JsonProperty("active_at")
    private Long activeAt;
}
