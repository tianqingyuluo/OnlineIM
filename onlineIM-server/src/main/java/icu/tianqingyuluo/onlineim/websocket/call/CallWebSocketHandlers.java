package icu.tianqingyuluo.onlineim.websocket.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import icu.tianqingyuluo.onlineim.service.call.CallFrames;
import icu.tianqingyuluo.onlineim.service.call.CallSignalingService;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.receiver.MessageReceiverHandler;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.sender.MessageSenderHandler;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.registry.SessionDelivery;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通话信令的 WebSocket 适配：每种 CALL_* 帧一个发送/接收处理器，业务集中在 CallSignalingService。
 */
@Configuration
public class CallWebSocketHandlers {

    @Bean public MessageSenderHandler callInviteSender(CallSignalingService service) { return sender(service, CallFrames.INVITE); }
    @Bean public MessageSenderHandler callOfferSender(CallSignalingService service) { return sender(service, CallFrames.OFFER); }
    @Bean public MessageSenderHandler callRingingSender(CallSignalingService service) { return sender(service, CallFrames.RINGING); }
    @Bean public MessageSenderHandler callAnswerSender(CallSignalingService service) { return sender(service, CallFrames.ANSWER); }
    @Bean public MessageSenderHandler callCandidateSender(CallSignalingService service) { return sender(service, CallFrames.CANDIDATE); }
    @Bean public MessageSenderHandler callRejectSender(CallSignalingService service) { return sender(service, CallFrames.REJECT); }
    @Bean public MessageSenderHandler callCancelSender(CallSignalingService service) { return sender(service, CallFrames.CANCEL); }
    @Bean public MessageSenderHandler callEndSender(CallSignalingService service) { return sender(service, CallFrames.END); }
    @Bean public MessageSenderHandler callHangupSender(CallSignalingService service) { return sender(service, CallFrames.HANGUP); }

    @Bean public MessageReceiverHandler callInviteReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.INVITE); }
    @Bean public MessageReceiverHandler callOfferReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.OFFER); }
    @Bean public MessageReceiverHandler callRingingReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.RINGING); }
    @Bean public MessageReceiverHandler callAnswerReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.ANSWER); }
    @Bean public MessageReceiverHandler callCandidateReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.CANDIDATE); }
    @Bean public MessageReceiverHandler callRejectReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.REJECT); }
    @Bean public MessageReceiverHandler callCancelReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.CANCEL); }
    @Bean public MessageReceiverHandler callBusyReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.BUSY); }
    @Bean public MessageReceiverHandler callEndReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.END); }
    @Bean public MessageReceiverHandler callHangupReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.HANGUP); }
    @Bean public MessageReceiverHandler callHandledElsewhereReceiver(CallFrameDelivery delivery) { return receiver(delivery, CallFrames.HANDLED_ELSEWHERE); }

    @Bean
    public CallFrameDelivery callFrameDelivery(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry) {
        return new CallFrameDelivery(objectMapper, sessionRegistry);
    }

    private static MessageSenderHandler sender(CallSignalingService service, String type) {
        return new CallFrameSender(service, type);
    }

    private static MessageReceiverHandler receiver(CallFrameDelivery delivery, String type) {
        return new CallFrameReceiver(delivery, type);
    }

    /** 入站帧：带上已认证会话交给通话服务。 */
    public static final class CallFrameSender implements MessageSenderHandler {
        private final CallSignalingService service;
        private final String type;

        public CallFrameSender(CallSignalingService service, String type) {
            this.service = service;
            this.type = type;
        }

        @Override
        public String getSupportedMessageType() {
            return type;
        }

        @Override
        public boolean publishMessage(WebSocketSession session, String message) {
            return service.handleInbound(session, type, message);
        }
    }

    /** 出站帧：Redis Stream 到达本节点后扇出到目标用户的在线连接。 */
    public static final class CallFrameReceiver implements MessageReceiverHandler {
        private final CallFrameDelivery delivery;
        private final String type;

        public CallFrameReceiver(CallFrameDelivery delivery, String type) {
            this.delivery = delivery;
            this.type = type;
        }

        @Override
        public String getSupportedMessageType() {
            return type;
        }

        @Override
        public boolean handleMessage(WebSocketMessageEvent event) {
            if (event == null || !type.equals(event.getType())) {
                return false;
            }
            return delivery.deliver(event);
        }
    }

    @Slf4j
    public static class CallFrameDelivery {
        static final String EXCLUDE = "exclude_connection_id";

        private final ObjectMapper objectMapper;
        private final LocalSessionRegistry sessionRegistry;

        public CallFrameDelivery(ObjectMapper objectMapper, LocalSessionRegistry sessionRegistry) {
            this.objectMapper = objectMapper;
            this.sessionRegistry = sessionRegistry;
        }

        public boolean deliver(WebSocketMessageEvent event) {
            try {
                JsonNode node = objectMapper.readTree(event.getMessage());
                if (!(node instanceof ObjectNode objectNode)) {
                    return false;
                }
                String exclude = text(objectNode, EXCLUDE);
                objectNode.remove(EXCLUDE);
                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("type", event.getType());
                frame.put("message", objectMapper.convertValue(objectNode, Map.class));
                String json = objectMapper.writeValueAsString(frame);
                List<String> receivers = event.getReceiverIDs() == null ? List.of() : event.getReceiverIDs();
                for (String userId : receivers) {
                    for (WebSocketSession session : SessionDelivery.of(sessionRegistry, userId)) {
                        if (exclude != null && exclude.equals(session.getConnectionId())) {
                            continue;
                        }
                        session.sendMessage(json);
                    }
                }
                return true;
            } catch (Exception ex) {
                log.warn("投递通话信令失败: type={}, error={}", event.getType(), ex.getMessage());
                return false;
            }
        }

        private static String text(ObjectNode node, String field) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            String text = value.asText();
            return text == null || text.isBlank() ? null : text;
        }
    }
}
