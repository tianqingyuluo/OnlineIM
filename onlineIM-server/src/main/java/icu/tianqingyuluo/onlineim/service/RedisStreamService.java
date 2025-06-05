package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.event.RedisStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Redis Stream 服务
 * 负责向 Redis Stream 发送消息和管理消息流
 */
@Slf4j
@Service
public class RedisStreamService {

    private static final String REDIS_STREAM_KEY = "im:message:stream";
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisStreamService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送消息到 Redis Stream
     * @param event Redis Stream 事件
     * @return 消息ID
     */
    public String publishMessage(RedisStreamEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            RecordId recordId = redisTemplate.opsForStream().add(
                REDIS_STREAM_KEY, 
                Collections.singletonMap("message", eventJson)
            );
            
            log.debug("消息已发送到 Redis Stream: stream={}, id={}, type={}, sender={}", 
                    REDIS_STREAM_KEY, recordId.getValue(), event.getType(), event.getSenderID());
            
            return recordId.getValue();
        } catch (JsonProcessingException e) {
            log.error("序列化 Redis Stream 事件失败: {}", e.getMessage(), e);
            throw new RuntimeException("发送消息到 Redis Stream 失败", e);
        } catch (Exception e) {
            log.error("发送消息到 Redis Stream 异常: {}", e.getMessage(), e);
            throw new RuntimeException("发送消息到 Redis Stream 失败", e);
        }
    }

    /**
     * 发送私聊消息到 Redis Stream
     * @param type 消息类型
     * @param senderID 发送者ID
     * @param message 消息内容
     * @param receiverID 接收者ID
     * @return 消息ID
     */
    public String publishPrivateMessage(String type, String senderID, String message, String receiverID) {
        RedisStreamEvent event = new RedisStreamEvent();
        event.setType(type);
        event.setSenderID(senderID);
        event.setMessage(message);
        event.setReceiverIDs(Collections.singletonList(receiverID));
        
        return publishMessage(event);
    }

    /**
     * 发送群聊消息到 Redis Stream
     * @param type 消息类型
     * @param senderID 发送者ID
     * @param message 消息内容
     * @param receiverIDs 接收者ID列表
     * @return 消息ID
     */
    public String publishGroupMessage(String type, String senderID, String message, List<String> receiverIDs) {
        RedisStreamEvent event = new RedisStreamEvent();
        event.setType(type);
        event.setSenderID(senderID);
        event.setMessage(message);
        event.setReceiverIDs(receiverIDs);
        
        return publishMessage(event);
    }

    public String publishRecallLog(String type, String operatorId, String message, List<String> receiverIDs) {
        RedisStreamEvent event = new RedisStreamEvent();
        event.setType(type);
        event.setSenderID(operatorId);
        event.setMessage(message);
        event.setReceiverIDs(receiverIDs);

        return publishMessage(event);
    }

    /**
     * 获取 Redis Stream 键名
     * @return Stream 键名
     */
    public String getStreamKey() {
        return REDIS_STREAM_KEY;
    }

    /**
     * 检查 Redis Stream 是否存在
     * @return 是否存在
     */
    public boolean streamExists() {
        try {
            return redisTemplate.hasKey(REDIS_STREAM_KEY);
        } catch (Exception e) {
            log.error("检查 Redis Stream 是否存在时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取 Redis Stream 长度
     * @return Stream 长度
     */
    public Long getStreamLength() {
        try {
            return redisTemplate.opsForStream().size(REDIS_STREAM_KEY);
        } catch (Exception e) {
            log.error("获取 Redis Stream 长度时发生异常: {}", e.getMessage(), e);
            return 0L;
        }
    }
}
