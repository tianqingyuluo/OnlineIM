package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.ReceiptRequest;
import icu.tianqingyuluo.onlineim.service.ReceiptService;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.springframework.stereotype.Component;

@Component
public class ReceiptMessageSender implements MessageSenderHandler {

    private final ObjectMapper objectMapper;
    private final ReceiptService receiptService;

    public ReceiptMessageSender(ObjectMapper objectMapper, ReceiptService receiptService) {
        this.objectMapper = objectMapper;
        this.receiptService = receiptService;
    }

    @Override
    public String getSupportedMessageType() {
        return "RECEIPT";
    }

    @Override
    public boolean publishMessage(WebSocketSession session, String message) {
        if (session == null || session.getUserId() == null) {
            return false;
        }
        try {
            return receiptService.handleDelivered(
                    session.getUserId(), objectMapper.readValue(message, ReceiptRequest.class));
        } catch (Exception ex) {
            return false;
        }
    }
}
