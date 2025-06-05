package icu.tianqingyuluo.onlineim.websocket.listener.handler.sender;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.pojo.dto.request.websocket.PrivateMessageRequest;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class PrivateMessageSender implements MessageSenderHandler {
    private final ObjectMapper objectMapper;
    private final PrivateMessageRepository privateMessageRepository;
    private final RedisStreamService redisStreamService;

    public PrivateMessageSender(ObjectMapper objectMapper, PrivateMessageRepository privateMessageRepository, RedisStreamService redisStreamService) {
        this.objectMapper = objectMapper;
        this.privateMessageRepository = privateMessageRepository;
        this.redisStreamService = redisStreamService;
    }

    @Override
    public String getSupportedMessageType() {
        return "PRIVATE_MESSAGE_REQUEST";
    }

    @SneakyThrows
    @Override
    public boolean publishMessage(String message) {

        PrivateMessageRequest request = objectMapper.readValue(message, PrivateMessageRequest.class);

        // 构建PrivateMessage对象
        PrivateMessage privateMessage = new PrivateMessage();
        privateMessage.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
        privateMessage.setConversationId(request.getConversationId());
        privateMessage.setSenderId(request.getSenderId());
        privateMessage.setReceiverId(request.getReceiverId());
        privateMessage.setMessageType(request.getMessageType());
        privateMessage.setContent(request.getContent());
        privateMessage.setStatus(0);
        privateMessage.setClientMessageId(request.getClientMessageId());
        privateMessage.setSeqId(IdUtil.getSnowflakeNextIdStr());
        privateMessage.setTimestamp(new Date());
        privateMessage.setCreatedAt(new Date());
        privateMessage.setUpdatedAt(new Date());

        privateMessageRepository.save(privateMessage); // 在mangodb存储

        // 推送到redis流
        redisStreamService.publishPrivateMessage(
                "PRIVATE_MESSAGE",
                request.getSenderId(),
                objectMapper.writeValueAsString(privateMessage),
                request.getReceiverId());

        return true;
    }


}
