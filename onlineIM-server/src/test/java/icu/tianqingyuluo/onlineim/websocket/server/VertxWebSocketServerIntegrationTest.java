package icu.tianqingyuluo.onlineim.websocket.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ServerIdentity;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.websocket.handler.HeartbeatHandler;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketAuthenticator;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketMessageRouter;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.MessageTypeSenderRegistry;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class VertxWebSocketServerIntegrationTest {

    private final WebSocketAuthenticator authenticator = mock(WebSocketAuthenticator.class);
    private final UserSessionService userSessionService = mock(UserSessionService.class);
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
    private final ServerIdentity serverIdentity = mock(ServerIdentity.class);
    private final LocalSessionRegistry sessionRegistry = new LocalSessionRegistry(mock(TaskScheduler.class));

    private VertxWebSocketServer server;
    private Vertx clientVertx;
    private HttpClient client;
    private int port;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(serverIdentity.getServerId()).thenReturn("serverID_integration");
        when(serverIdentity.getGroupId()).thenReturn("im-msg-grp-serverID_integration");
        when(authenticator.extractTokenFromUri(anyString())).thenReturn("Bearer test-token");
        when(authenticator.authenticate(anyString())).thenReturn(
                WebSocketAuthenticator.AuthResult.success(
                        "tester", "usr_integration", "device_integration", "Bearer test-token", 60_000
                )
        );

        ObjectMapper objectMapper = new ObjectMapper();
        HeartbeatHandler heartbeatHandler = new HeartbeatHandler(objectMapper, userSessionService);
        WebSocketMessageRouter messageRouter = new WebSocketMessageRouter(
                objectMapper,
                new MessageTypeSenderRegistry(List.of()),
                heartbeatHandler
        );
        server = new VertxWebSocketServer(
                authenticator, messageRouter, sessionRegistry, userSessionService, redisTemplate, serverIdentity
        );
        ReflectionTestUtils.setField(server, "port", 0);
        ReflectionTestUtils.setField(server, "websocketPath", "/api/v1/chat");
        server.start();

        HttpServer httpServer = (HttpServer) ReflectionTestUtils.getField(server, "httpServer");
        port = httpServer.actualPort();
        clientVertx = Vertx.vertx();
        client = clientVertx.createHttpClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
        if (client != null) {
            client.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        if (clientVertx != null) {
            clientVertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void heartbeatReceivesAckThroughRealWebSocketConnection() throws Exception {
        WebSocket socket = connect("/api/v1/chat?token=Bearer%20test-token");
        CountDownLatch ackLatch = new CountDownLatch(1);
        socket.textMessageHandler(message -> {
            try {
                JsonNode json = new ObjectMapper().readTree(message);
                if ("HEARTBEAT_ACK".equals(json.path("type").asText())) {
                    ackLatch.countDown();
                }
            } catch (Exception ignored) {
            }
        });

        socket.writeTextMessage("{\"type\":\"HEARTBEAT\",\"message\":{}}");

        assertTrue(ackLatch.await(3, TimeUnit.SECONDS));
        assertEquals(1, sessionRegistry.getOnlineCount());
        socket.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void inactiveConnectionIsClosedAndRemoved() throws Exception {
        WebSocket socket = connect("/api/v1/chat?token=Bearer%20test-token");
        CountDownLatch closeLatch = new CountDownLatch(1);
        socket.closeHandler(ignored -> closeLatch.countDown());

        Thread.sleep(30);
        sessionRegistry.cleanupInactiveSessions(10);

        assertTrue(closeLatch.await(3, TimeUnit.SECONDS));
        assertEquals(0, sessionRegistry.getOnlineCount());
    }

    @Test
    void rejectsConnectionsOutsideConfiguredPath() {
        assertThrows(Exception.class, () -> connect("/wrong?token=Bearer%20test-token"));
        assertEquals(0, sessionRegistry.getOnlineCount());
    }

    private WebSocket connect(String uri) throws Exception {
        WebSocketConnectOptions options = new WebSocketConnectOptions()
                .setHost("127.0.0.1")
                .setPort(port)
                .setURI(uri)
                .setConnectTimeout(Duration.ofSeconds(3).toMillis());
        return client.webSocket(options).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
