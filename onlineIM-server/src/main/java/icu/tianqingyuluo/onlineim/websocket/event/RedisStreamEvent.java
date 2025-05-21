package icu.tianqingyuluo.onlineim.websocket.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedisStreamEvent {
    private String type;
    private String senderID;
    private String message;
    private String senderChannelID;
    private List<String> receiverIDs;
}
