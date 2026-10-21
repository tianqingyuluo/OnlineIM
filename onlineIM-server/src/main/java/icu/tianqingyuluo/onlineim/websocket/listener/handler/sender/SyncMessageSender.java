package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.SyncRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.OfflineSyncResponse;
import icu.tianqingyuluo.onlineim.service.OfflineSyncService;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketFrameSender;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 客户端连接或重连后发来的补齐请求。身份只取自已认证会话，不信任载荷里的用户字段。
 */
@Slf4j
@Component
public class SyncMessageSender implements MessageSenderHandler {

    public static final String REQUEST_TYPE = "SYNC_REQUEST";
    public static final String RESPONSE_TYPE = "OFFLINE_SYNC";

    private final ObjectMapper objectMapper;
    private final OfflineSyncService offlineSyncService;
    private final WebSocketFrameSender frameSender;

    public SyncMessageSender(ObjectMapper objectMapper,
                             OfflineSyncService offlineSyncService,
                             WebSocketFrameSender frameSender) {
        this.objectMapper = objectMapper;
        this.offlineSyncService = offlineSyncService;
        this.frameSender = frameSender;
    }

    @Override
    public String getSupportedMessageType() {
        return REQUEST_TYPE;
    }

    @Override
    public boolean publishMessage(WebSocketSession session, String message) {
        if (session == null || session.getUserId() == null || session.getUserId().isBlank()) {
            return false;
        }
        try {
            SyncRequest request = message == null || message.isBlank()
                    ? new SyncRequest()
                    : objectMapper.readValue(message, SyncRequest.class);
            OfflineSyncResponse response = offlineSyncService.sync(session.getUserId(), request);
            return frameSender.send(session, RESPONSE_TYPE, response);
        } catch (Exception ex) {
            log.warn("处理上线补齐请求失败: userId={}, error={}", session.getUserId(), ex.getMessage());
            return false;
        }
    }
}
