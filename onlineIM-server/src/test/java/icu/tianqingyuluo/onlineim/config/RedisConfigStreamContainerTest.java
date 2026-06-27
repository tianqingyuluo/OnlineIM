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
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 集成测试：验证 RedisConfig 使用消费者组模式注册 Stream 容器。
 */
@Testcontainers
class RedisConfigStreamContainerTest {

    private static final String STREAM_KEY = "im:message:stream";

    @Container
    private final RedisContainer redis = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, Object> redisTemplate;
    private StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamContainer;

    @BeforeEach
    void setUp() {
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
        if (streamContainer != null) {
            CountDownLatch latch = new CountDownLatch(1);
            streamContainer.stop(latch::countDown);
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            await().atMost(Duration.ofSeconds(1)).until(() -> !streamContainer.isRunning());
        }
        connectionFactory.destroy();
    }

    @Test
    void streamContainerCreatesConsumerGroupAndReadsOnlyNewMessages() throws Exception {
        RecordId historicalId = addEvent("historical", "usr_old", List.of("usr_receiver"));

        String groupId = "im-msg-grp-serverID_config";
        String serverId = "serverID_config";
        ServerIdentity identity = stubIdentity(groupId, serverId);

        List<WebSocketMessageEvent> received = new CopyOnWriteArrayList<>();
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        when(registry.hasUserOnline(anyList())).thenReturn(true);

        RedisEventListener listener = new RedisEventListener(
                event -> received.add((WebSocketMessageEvent) event),
                objectMapper, registry, redisTemplate, identity);

        RedisConfig redisConfig = new RedisConfig();
        streamContainer = redisConfig.streamContainer(connectionFactory, listener, redisTemplate, identity);

        RecordId freshId = addEvent("fresh", "usr_new", List.of("usr_receiver"));

        await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 1);

        assertEquals("fresh", received.get(0).getMessage());
        assertEquals("usr_new", received.get(0).getSenderID());

        StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(STREAM_KEY);
        assertEquals(1, groups.groupCount(), "应创建一个消费者组");

        StreamInfo.XInfoGroup group = groups.get(0);
        assertEquals(groupId, group.groupName());
        assertEquals(freshId.getValue(), group.lastDeliveredId(), "消费者组应通过 XREADGROUP 推进到最新消息");
        assertEquals(0, group.pendingCount(), "消息处理完成后不应残留 pending");
        assertNotNull(historicalId, "历史消息应成功写入流");
    }

    private RecordId addEvent(String message, String senderId, List<String> receiverIds) throws Exception {
        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", senderId, message, receiverIds);
        String json = objectMapper.writeValueAsString(event);
        return redisTemplate.opsForStream().add(STREAM_KEY, java.util.Collections.singletonMap("message", json));
    }

    private ServerIdentity stubIdentity(String groupId, String serverId) {
        ServerIdentity identity = mock(ServerIdentity.class);
        when(identity.getGroupId()).thenReturn(groupId);
        when(identity.getServerId()).thenReturn(serverId);
        return identity;
    }
}
