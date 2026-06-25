package icu.tianqingyuluo.onlineim.websocket.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.event.RedisStreamEvent;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Stream 事件监听器
 * 负责监听 Redis Stream 中的消息，并将其转换为 Spring 事件
 */

@Slf4j
@Component
public class RedisEventListener implements StreamListener<String, ObjectRecord<String, String>> {

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final LocalSessionRegistry sessionRegistry;

    @Autowired
    public RedisEventListener(ApplicationEventPublisher eventPublisher,
                              ObjectMapper objectMapper,
                              LocalSessionRegistry sessionRegistry) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
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
                log.debug("消息接收者不在当前服务器上连接，忽略消息: {}", redisEvent.getType());
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
            
            // // 直接向接收者发送消息（如果需要的话）
            // for (String receiverId : redisEvent.getReceiverIDs()) {
            //     String connectionId = LocalChannelRegistry.getConnectionIDByUserID(receiverId);
            //     if (connectionId != null) {
            //         Channel channel = LocalChannelRegistry.get(connectionId);
            //         if (channel != null && channel.isActive()) {
            //             // 创建响应消息
            //             String responseJson = objectMapper.writeValueAsString(wsEvent);
            //             channel.writeAndFlush(new TextWebSocketFrame(responseJson));
            //             log.debug("已向用户 {} 发送消息", receiverId);
            //         }
            //     }
            // }
        } catch (Exception e) {
            log.error("处理 Redis Stream 消息异常: {}", e.getMessage(), e);
        }
    }

    private boolean hasReceiverConnected(List<String> receiverIDs) {
        return sessionRegistry.hasUserOnline(receiverIDs);
    }
}