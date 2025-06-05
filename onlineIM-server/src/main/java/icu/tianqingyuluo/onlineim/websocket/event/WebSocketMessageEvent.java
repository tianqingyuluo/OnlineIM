package icu.tianqingyuluo.onlineim.websocket.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessageEvent {
    private String type;
    private String senderID;
    private String message;
    private List<String> receiverIDs;
}
