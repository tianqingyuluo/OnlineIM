package icu.tianqingyuluo.onlineim.pojo.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonSerialize
public class RedisConnectionMeta {
    String connectionID;
    String currentNodeIP;
    long activeTime;
}
