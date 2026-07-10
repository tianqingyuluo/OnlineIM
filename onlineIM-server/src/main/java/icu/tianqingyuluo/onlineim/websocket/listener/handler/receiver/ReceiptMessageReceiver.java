package icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.response.ReceiptEventPayload;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketFrameSender;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.MessageTypeReceiverRegistry;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.springframework.stereotype.Component;

@Component
public class ReceiptMessageReceiver implements MessageReceiverHandler {

    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;
    private final WebSocketFrameSender frameSender;

    public ReceiptMessageReceiver(ObjectMapper objectMapper,
                                  LocalSessionRegistry sessionRegistry,
                                  WebSocketFrameSender frameSender) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.frameSender = frameSender;
    }

    @Override
    public String getSupportedMessageType() {
        return "RECEIPT";
    }

    @Override
    public boolean handleMessage(WebSocketMessageEvent event) {
        try {
            ReceiptEventPayload payload = objectMapper.readValue(event.getMessage(), ReceiptEventPayload.class);
            boolean delivered = false;
            if (event.getReceiverIDs() != null) {
                for (String receiverId : event.getReceiverIDs()) {
                    WebSocketSession session = sessionRegistry.getByUserId(receiverId);
                    delivered |= frameSender.send(session, "RECEIPT", payload);
                }
            }
            return delivered || event.getReceiverIDs() == null || event.getReceiverIDs().isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }
}
