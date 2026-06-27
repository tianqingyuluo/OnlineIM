package icu.tianqingyuluo.onlineim.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import icu.tianqingyuluo.onlineim.websocket.event.RedisStreamEvent;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.listener.RedisEventListener;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 集成测试：验证新实例创建新 group 时从流尾开始消费，不重放历史消息。
 */
@Testcontainers
class RedisStreamRestartIntegrationTest {

    private static final String STREAM_KEY = "im:message:stream";

    @Container
    private final RedisContainer redis = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<StreamMessageListenerContainer<String, ObjectRecord<String, String>>> containers = new ArrayList<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        shuttingDown.set(false);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        shuttingDown.set(true);
        for (var container : containers) {
            try {
                CountDownLatch latch = new CountDownLatch(1);
                container.stop(latch::countDown);
                latch.await(1, TimeUnit.SECONDS);
                await().atMost(Duration.ofSeconds(1)).until(() -> !container.isRunning());
            } catch (Exception ignored) {
            }
        }
        containers.clear();
        connectionFactory.destroy();
    }

    @Test
    void newGroupStartsFromTailAndDoesNotReplayHistoricalMessages() throws Exception {
        addStreamEvent("historical", "usr_old", List.of("usr_receiver"));

        String groupId = "im-msg-grp-serverID_restart";
        redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("$"), groupId);

        List<WebSocketMessageEvent> received = new CopyOnWriteArrayList<>();
        ServerIdentity identity = stubIdentity(groupId, "serverID_restart");
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        when(registry.hasUserOnline(anyList())).thenReturn(true);

        RedisEventListener listener = new RedisEventListener(
                event -> received.add((WebSocketMessageEvent) event),
                objectMapper, registry, redisTemplate, identity);

        startContainer(groupId, "serverID_restart", listener);

        addStreamEvent("fresh", "usr_new", List.of("usr_receiver"));

        await().atMost(Duration.ofSeconds(5)).until(() -> !received.isEmpty());

        assertEquals(1, received.size(), "不应重放历史消息，只应收到创建 group 之后的新消息");
        assertEquals("fresh", received.get(0).getMessage());
        assertEquals("usr_new", received.get(0).getSenderID());
        assertTrue(pendingCount(groupId) == 0, "消息应已 ack，不应残留在 PEL");
    }

    private void addStreamEvent(String message, String senderId, List<String> receiverIds) throws Exception {
        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", senderId, message, receiverIds);
        String json = objectMapper.writeValueAsString(event);
        redisTemplate.opsForStream().add(STREAM_KEY, java.util.Collections.singletonMap("message", json));
    }

    private void startContainer(String groupId, String consumerName, RedisEventListener listener) {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .errorHandler(this::handleContainerError)
                        .targetType(String.class)
                        .build();
        StreamMessageListenerContainer<String, ObjectRecord<String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);
        container.receive(
                Consumer.from(groupId, consumerName),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                listener);
        container.start();
        containers.add(container);
    }

    private long pendingCount(String groupId) {
        return redisTemplate.opsForStream().pending(STREAM_KEY, groupId).getTotalPendingMessages();
    }

    private ServerIdentity stubIdentity(String groupId, String serverId) {
        ServerIdentity identity = mock(ServerIdentity.class);
        when(identity.getGroupId()).thenReturn(groupId);
        when(identity.getServerId()).thenReturn(serverId);
        return identity;
    }

    private void handleContainerError(Throwable throwable) {
        if (shuttingDown.get() && isConnectionClosed(throwable)) {
            return;
        }
        throw new AssertionError("Unexpected Redis stream listener error", throwable);
    }

    private boolean isConnectionClosed(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("connection closed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
