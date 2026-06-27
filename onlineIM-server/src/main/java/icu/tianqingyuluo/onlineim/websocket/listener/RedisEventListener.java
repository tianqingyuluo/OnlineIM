package icu.tianqingyuluo.onlineim.websocket.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ServerIdentity;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.event.RedisStreamEvent;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Stream 事件监听器
 * 负责监听 Redis Stream 中的消息，并将其转换为 Spring 事件
 * 以消费者组模式消费：每实例独立 group（广播），处理完成后 XACK
 */

@Slf4j
@Component
public class RedisEventListener implements StreamListener<String, ObjectRecord<String, String>> {

    private static final String STREAM_KEY = "im:message:stream";

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ServerIdentity serverIdentity;

    public RedisEventListener(ApplicationEventPublisher eventPublisher,
                              ObjectMapper objectMapper,
                              LocalSessionRegistry sessionRegistry,
                              RedisTemplate<String, Object> redisTemplate,
                              ServerIdentity serverIdentity) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.redisTemplate = redisTemplate;
        this.serverIdentity = serverIdentity;
    }

    @Override
    public void onMessage(ObjectRecord<String, String> message) {
        String stream = message.getStream();
        String messageId = message.getId().getValue();
        String eventData = message.getValue();

        log.debug("从 Redis Stream 收到消息: stream={}, id={}", stream, messageId);

        try {
            // 解析 Redis Stream 事件
            RedisStreamEvent redisEvent = objectMapper.readValue(eventData, RedisStreamEvent.class);

            // 检查是否有接收者在当前服务器上连接
            if (!hasReceiverConnected(redisEvent.getReceiverIDs()) && !hasReceiverConnected(List.of(redisEvent.getSenderID()))) {
                log.debug("消息接收者不在当前服务器上连接，仅 ack: {}", redisEvent.getType());
                ack(stream, messageId);
                return;
            }

            log.info("处理 Redis Stream 消息: type={}, sender={}, receivers={}",
                    redisEvent.getType(), redisEvent.getSenderID(), redisEvent.getReceiverIDs());

            // 创建WebSocket消息事件（用于本地处理）
            WebSocketMessageEvent wsEvent = WebSocketMessageEvent.builder()
                    .type(redisEvent.getType())
                    .senderID(redisEvent.getSenderID())
                    .message(redisEvent.getMessage())
                    .receiverIDs(redisEvent.getReceiverIDs())
                    .build();

            // 发布到Spring事件总线，触发相应的事件监听器
            eventPublisher.publishEvent(wsEvent);

            // 处理完成后 ack（at-most-once 语义，异常时留 PEL 留痕但不自动重投）
            ack(stream, messageId);
        } catch (Exception e) {
            // 不 ack，消息留在 PEL 便于排查（不自动重投）
            log.error("处理 Redis Stream 消息异常，留 PEL: stream={}, id={}, err={}", stream, messageId, e.getMessage(), e);
        }
    }

    private boolean hasReceiverConnected(List<String> receiverIDs) {
        return sessionRegistry.hasUserOnline(receiverIDs);
    }

    private void ack(String stream, String messageId) {
        try {
            redisTemplate.opsForStream().acknowledge(stream, serverIdentity.getGroupId(), messageId);
        } catch (Exception e) {
            log.warn("XACK 失败: stream={}, id={}, group={}, err={}", stream, messageId, serverIdentity.getGroupId(), e.getMessage());
        }
    }
}
