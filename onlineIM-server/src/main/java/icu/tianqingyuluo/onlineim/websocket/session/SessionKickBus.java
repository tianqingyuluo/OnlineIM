package icu.tianqingyuluo.onlineim.websocket.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 挤下线通知。本节点先处理，再经 Redis 广播，让持有旧连接的其他实例也能关掉它。
 */
@Slf4j
@Component
public class SessionKickBus implements MessageListener {

    public static final String CHANNEL = "im:session:kick";

    private final LocalSessionRegistry sessionRegistry;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory;
    private RedisMessageListenerContainer container;

    public SessionKickBus(LocalSessionRegistry sessionRegistry,
                          StringRedisTemplate stringRedisTemplate,
                          ObjectMapper objectMapper,
                          org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
        this.sessionRegistry = sessionRegistry;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.connectionFactory = connectionFactory;
    }

    @PostConstruct
    public void start() {
        try {
            container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(this, new ChannelTopic(CHANNEL));
            container.afterPropertiesSet();
            container.start();
        } catch (Exception e) {
            log.warn("挤下线订阅启动失败，本节点仍会处理本地连接: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    /**
     * @param envelopeJson {@code {"user_id","device_id","frame"}}，frame 是发给客户端的 SESSION_KICKED
     */
    public void publish(String userId, String deviceId, String envelopeJson) {
        String frameJson = frameOf(envelopeJson);
        if (frameJson != null) {
            sessionRegistry.kickDevice(userId, deviceId, frameJson);
        }
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, envelopeJson);
        } catch (Exception e) {
            log.warn("发布挤下线通知失败: userId={}, deviceId={}, error={}", userId, deviceId, e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        apply(body);
    }

    /**
     * 按信封关掉本节点上的对应连接。重复投递是无害的：连接已经不在时直接返回。
     */
    public void apply(String envelopeJson) {
        try {
            JsonNode node = objectMapper.readTree(envelopeJson);
            String userId = text(node, "user_id");
            String deviceId = text(node, "device_id");
            JsonNode frame = node.get("frame");
            if (userId == null || deviceId == null || frame == null || frame.isNull()) {
                return;
            }
            sessionRegistry.kickDevice(userId, deviceId, objectMapper.writeValueAsString(frame));
        } catch (Exception e) {
            log.warn("处理挤下线通知失败: {}", e.getMessage());
        }
    }

    private String frameOf(String envelopeJson) {
        try {
            JsonNode frame = objectMapper.readTree(envelopeJson).get("frame");
            return frame == null || frame.isNull() ? null : objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("挤下线帧无效: {}", e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
