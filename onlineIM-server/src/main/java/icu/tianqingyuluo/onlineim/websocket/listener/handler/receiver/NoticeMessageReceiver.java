package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.util.LocalChannelRegistry;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NoticeMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;

    public NoticeMessageReceiver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSupportedMessageType() {
        return "NOTICE_MESSAGE";
    }

    @Override
    @SneakyThrows
    public boolean handleMessage(WebSocketMessageEvent event) {
        if (!getSupportedMessageType().equals(event.getType())) {
            return false;
        }

        List<String> receiverIds = event.getReceiverIDs();
        String messageScope = event.getMessage();

        log.info("返回提示信息给{}", receiverIds);

        for (String receiverId : receiverIds) {
            String connectionId = LocalChannelRegistry.getConnectionIDByUserID(receiverId);
            if (connectionId != null) {
                Channel receiverChannel = LocalChannelRegistry.get(connectionId);
                if (receiverChannel != null) {
                    Map<String, String> response = new HashMap<>();
                    response.put("type", "NOTICE_MESSAGE_RESPONSE");
                    response.put("message", messageScope);
                    String responseJson = objectMapper.writeValueAsString(response);
                    receiverChannel.writeAndFlush(new TextWebSocketFrame(responseJson));
                    log.debug("已经向 {} 发送更新提醒", receiverId);
                }
            }
        }
        return true;
    }
}
