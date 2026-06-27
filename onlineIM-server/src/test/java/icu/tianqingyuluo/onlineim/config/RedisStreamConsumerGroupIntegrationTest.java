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
 * 集成测试：验证 Redis Stream 消费者组的广播与 ack 语义。
 */
@Testcontainers
class RedisStreamConsumerGroupIntegrationTest {

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
    void twoGroupsBothReceiveSameMessageAndAckIndependently() throws Exception {
        String groupA = "im-msg-grp-serverID_A";
        String groupB = "im-msg-grp-serverID_B";
        redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("$"), groupA);
        redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("$"), groupB);

        List<WebSocketMessageEvent> receivedA = new CopyOnWriteArrayList<>();
        List<WebSocketMessageEvent> receivedB = new CopyOnWriteArrayList<>();

        ServerIdentity identityA = stubIdentity(groupA, "serverID_A");
        ServerIdentity identityB = stubIdentity(groupB, "serverID_B");

        LocalSessionRegistry registryA = mock(LocalSessionRegistry.class);
        when(registryA.hasUserOnline(anyList())).thenReturn(true);
        LocalSessionRegistry registryB = mock(LocalSessionRegistry.class);
        when(registryB.hasUserOnline(anyList())).thenReturn(true);

        RedisEventListener listenerA = new RedisEventListener(
                event -> receivedA.add((WebSocketMessageEvent) event),
                objectMapper, registryA, redisTemplate, identityA);
        RedisEventListener listenerB = new RedisEventListener(
                event -> receivedB.add((WebSocketMessageEvent) event),
                objectMapper, registryB, redisTemplate, identityB);

        startContainer(groupA, "serverID_A", listenerA);
        startContainer(groupB, "serverID_B", listenerB);

        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", "usr_sender", "hello",
                List.of("usr_receiver"));
        String json = objectMapper.writeValueAsString(event);
        redisTemplate.opsForStream().add(STREAM_KEY,
                java.util.Collections.singletonMap("message", json));

        await().atMost(Duration.ofSeconds(5)).until(() -> receivedA.size() == 1 && receivedB.size() == 1);

        assertEquals("hello", receivedA.get(0).getMessage());
        assertEquals("hello", receivedB.get(0).getMessage());
        assertEquals("usr_sender", receivedA.get(0).getSenderID());

        assertEquals(0, pendingCount(groupA));
        assertEquals(0, pendingCount(groupB));
    }

    @Test
    void noLocalConnection_stillAcksWithoutPublishing() throws Exception {
        String group = "im-msg-grp-serverID_C";
        redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("$"), group);

        List<WebSocketMessageEvent> received = new CopyOnWriteArrayList<>();
        ServerIdentity identity = stubIdentity(group, "serverID_C");
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        when(registry.hasUserOnline(anyList())).thenReturn(false);

        RedisEventListener listener = new RedisEventListener(
                event -> received.add((WebSocketMessageEvent) event),
                objectMapper, registry, redisTemplate, identity);

        startContainer(group, "serverID_C", listener);

        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", "usr_sender", "hello",
                List.of("usr_receiver"));
        String json = objectMapper.writeValueAsString(event);
        redisTemplate.opsForStream().add(STREAM_KEY,
                java.util.Collections.singletonMap("message", json));

        await().atMost(Duration.ofSeconds(5)).until(() -> pendingCount(group) == 0);

        assertTrue(received.isEmpty());
        assertEquals(0, pendingCount(group));
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

    private long pendingCount(String group) {
        return redisTemplate.opsForStream().pending(STREAM_KEY, group).getTotalPendingMessages();
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
