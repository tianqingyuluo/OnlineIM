package icu.tianqingyuluo.onlineim.websocket.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ServerIdentity;
import icu.tianqingyuluo.onlineim.websocket.event.RedisStreamEvent;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RedisEventListenerTest {

    private ApplicationEventPublisher eventPublisher;
    private LocalSessionRegistry sessionRegistry;
    private RedisTemplate<String, Object> redisTemplate;
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
    private ServerIdentity serverIdentity;
    private RedisEventListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String STREAM_KEY = "im:message:stream";
    private static final String GROUP_ID = "im-msg-grp-serverID_test";
    private static final String MESSAGE_ID = "1234567890-0";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        sessionRegistry = mock(LocalSessionRegistry.class);
        redisTemplate = mock(RedisTemplate.class);
        serverIdentity = mock(ServerIdentity.class);

        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(serverIdentity.getGroupId()).thenReturn(GROUP_ID);

        listener = new RedisEventListener(eventPublisher, objectMapper, sessionRegistry,
                redisTemplate, serverIdentity);
    }

    private ObjectRecord<String, String> buildRecord(RedisStreamEvent event) throws Exception {
        String json = objectMapper.writeValueAsString(event);
        return ObjectRecord.<String, String>create(STREAM_KEY, json).withId(RecordId.of(MESSAGE_ID));
    }

    @Test
    void localReceiverConnected_publishesEventAndAcks() throws Exception {
        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", "usr_sender", "hello",
                List.of("usr_receiver"));
        when(sessionRegistry.hasUserOnline(List.of("usr_receiver"))).thenReturn(true);

        listener.onMessage(buildRecord(event));

        verify(eventPublisher).publishEvent(any(WebSocketMessageEvent.class));
        verify(streamOps).acknowledge(eq(STREAM_KEY), eq(GROUP_ID), eq(MESSAGE_ID));
    }

    @Test
    void localSenderConnected_publishesEventAndAcks() throws Exception {
        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", "usr_sender", "hello",
                List.of("usr_receiver"));
        when(sessionRegistry.hasUserOnline(List.of("usr_receiver"))).thenReturn(false);
        when(sessionRegistry.hasUserOnline(List.of("usr_sender"))).thenReturn(true);

        listener.onMessage(buildRecord(event));

        verify(eventPublisher).publishEvent(any(WebSocketMessageEvent.class));
        verify(streamOps).acknowledge(eq(STREAM_KEY), eq(GROUP_ID), eq(MESSAGE_ID));
    }

    @Test
    void noLocalConnection_onlyAcksWithoutPublishing() throws Exception {
        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", "usr_sender", "hello",
                List.of("usr_receiver"));
        when(sessionRegistry.hasUserOnline(anyList())).thenReturn(false);

        listener.onMessage(buildRecord(event));

        verify(eventPublisher, never()).publishEvent(any());
        verify(streamOps).acknowledge(eq(STREAM_KEY), eq(GROUP_ID), eq(MESSAGE_ID));
    }

    @Test
    void deserializeFails_doesNotAck() throws Exception {
        ObjectRecord<String, String> record = ObjectRecord.<String, String>create(STREAM_KEY, "not-json")
                .withId(RecordId.of(MESSAGE_ID));

        listener.onMessage(record);

        verify(eventPublisher, never()).publishEvent(any());
        verify(streamOps, never()).acknowledge(any(), any(String.class), any(String.class));
    }

    @Test
    void publishEventThrows_doesNotAck() throws Exception {
        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", "usr_sender", "hello",
                List.of("usr_receiver"));
        when(sessionRegistry.hasUserOnline(anyList())).thenReturn(true);
        doThrow(new RuntimeException("event bus down")).when(eventPublisher).publishEvent(any(WebSocketMessageEvent.class));

        listener.onMessage(buildRecord(event));

        verify(streamOps, never()).acknowledge(any(), any(String.class), any(String.class));
    }

    @Test
    void ackFailure_doesNotPropagateException() throws Exception {
        RedisStreamEvent event = new RedisStreamEvent("PRIVATE_MESSAGE", "usr_sender", "hello",
                List.of("usr_receiver"));
        when(sessionRegistry.hasUserOnline(List.of("usr_receiver"))).thenReturn(true);
        doThrow(new RuntimeException("redis down")).when(streamOps)
                .acknowledge(any(), any(String.class), any(String.class));

        // 不应抛异常
        listener.onMessage(buildRecord(event));

        verify(eventPublisher).publishEvent(any(WebSocketMessageEvent.class));
    }

    private static <T> List<T> anyList() {
        return org.mockito.ArgumentMatchers.anyList();
    }
}
