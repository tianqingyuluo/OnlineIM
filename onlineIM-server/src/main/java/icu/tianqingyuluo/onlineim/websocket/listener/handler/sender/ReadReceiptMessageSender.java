package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.ReadReceiptRequest;
import icu.tianqingyuluo.onlineim.service.ReceiptService;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import org.springframework.stereotype.Component;

@Component
public class ReadReceiptMessageSender implements MessageSenderHandler {

    private final ObjectMapper objectMapper;
    private final ReceiptService receiptService;

    public ReadReceiptMessageSender(ObjectMapper objectMapper, ReceiptService receiptService) {
        this.objectMapper = objectMapper;
        this.receiptService = receiptService;
    }

    @Override
    public String getSupportedMessageType() {
        return "READ_RECEIPT";
    }

    @Override
    public boolean publishMessage(WebSocketSession session, String message) {
        if (session == null || session.getUserId() == null) {
            return false;
        }
        try {
            return receiptService.handleRead(
                    session.getUserId(), objectMapper.readValue(message, ReadReceiptRequest.class));
        } catch (Exception ex) {
            return false;
        }
    }
}
