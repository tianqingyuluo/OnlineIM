package icu.tianqingyuluo.onlineim.pojo.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonSerialize
public class RedisConnectionMeta {
    String token;
    String connectionID;
    String currentNodeIP;
    long activeTime;
    /** 登录用户，设备列表和挤下线都按它定位 */
    String userId;
    String deviceId;
    /** web / desktop / phone */
    String deviceType;
    /** 本次登录时间（epoch millis） */
    Long loginTime;

    public RedisConnectionMeta(String token, String connectionID, String currentNodeIP, long activeTime) {
        this.token = token;
        this.connectionID = connectionID;
        this.currentNodeIP = currentNodeIP;
        this.activeTime = activeTime;
    }
}
