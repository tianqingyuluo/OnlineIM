package icu.tianqingyuluo.onlineim.service.call;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.document.PrivateMessage;
import icu.tianqingyuluo.onlineim.repository.PrivateMessageRepository;
import icu.tianqingyuluo.onlineim.service.ConversationRecordService;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把通话结果写成一条私聊消息（message_type=call），并更新双方会话预览。
 * client_message_id 使用 call_id，重复的终态不会再落一条。
 */
@Slf4j
@Service
public class CallRecordService {

    private final PrivateMessageRepository privateMessageRepository;
    private final ConversationRecordService conversationRecords;
    private final RedisStreamService redisStreamService;
    private final ObjectMapper objectMapper;

    public CallRecordService(PrivateMessageRepository privateMessageRepository,
                             ConversationRecordService conversationRecords,
                             RedisStreamService redisStreamService,
                             ObjectMapper objectMapper) {
        this.privateMessageRepository = privateMessageRepository;
        this.conversationRecords = conversationRecords;
        this.redisStreamService = redisStreamService;
        this.objectMapper = objectMapper;
    }

    public void record(CallSession session, String status, int durationSeconds) {
        if (session == null || session.getCallId() == null || session.getCallerId() == null || session.getCalleeId() == null) {
            return;
        }
        try {
            if (privateMessageRepository.findBySenderIdAndClientMessageId(session.getCallerId(), session.getCallId()) != null) {
                return;
            }
            int duration = Math.max(0, durationSeconds);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("call_id", session.getCallId());
            body.put("call_type", session.getCallType());
            body.put("call_status", status);
            body.put("duration_seconds", duration);

            PrivateMessage message = new PrivateMessage();
            message.setId("msg_" + IdUtil.getSnowflakeNextIdStr());
            message.setConversationId(session.getConversationId());
            message.setSenderId(session.getCallerId());
            message.setReceiverId(session.getCalleeId());
            message.setMessageType("call");
            message.setContent(objectMapper.writeValueAsString(body));
            message.setExt(body);
            message.setContentRevision(1);
            message.setStatus(0);
            message.setClientMessageId(session.getCallId());
            message.setSeqId(IdUtil.getSnowflakeNextIdStr());
            Date now = new Date();
            message.setTimestamp(now);
            message.setCreatedAt(now);
            message.setUpdatedAt(now);

            privateMessageRepository.save(message);
            conversationRecords.onPrivateMessage(message);
            redisStreamService.publishPrivateMessage(
                    "PRIVATE_MESSAGE",
                    session.getCallerId(),
                    objectMapper.writeValueAsString(message),
                    session.getCalleeId());
        } catch (JsonProcessingException ex) {
            log.warn("序列化通话记录失败: callId={}, error={}", session.getCallId(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("保存通话记录失败: callId={}, error={}", session.getCallId(), ex.getMessage());
        }
    }
}
