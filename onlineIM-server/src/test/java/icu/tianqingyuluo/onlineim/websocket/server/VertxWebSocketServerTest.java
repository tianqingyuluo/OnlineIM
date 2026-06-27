package icu.tianqingyuluo.onlineim.websocket.server;

import icu.tianqingyuluo.onlineim.config.ServerIdentity;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketAuthenticator;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketMessageRouter;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class VertxWebSocketServerTest {

    private static final String STREAM_KEY = "im:message:stream";
    private static final String GROUP_ID = "im-msg-grp-serverID_test";
    private static final String SERVER_ID = "serverID_test";

    private final WebSocketAuthenticator authenticator = mock(WebSocketAuthenticator.class);
    private final WebSocketMessageRouter messageRouter = mock(WebSocketMessageRouter.class);
    private final LocalSessionRegistry sessionRegistry = mock(LocalSessionRegistry.class);
    private final UserSessionService userSessionService = mock(UserSessionService.class);
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final ServerIdentity serverIdentity = mock(ServerIdentity.class);

    private VertxWebSocketServer server;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(serverIdentity.getGroupId()).thenReturn(GROUP_ID);
        when(serverIdentity.getServerId()).thenReturn(SERVER_ID);

        server = new VertxWebSocketServer(authenticator, messageRouter, sessionRegistry,
                userSessionService, redisTemplate, serverIdentity);
    }

    @Test
    void stopDestroysConsumerGroupAndUnregistersService() {
        server.stop();

        verify(streamOperations).destroyGroup(STREAM_KEY, GROUP_ID);
        verify(hashOperations).delete("websocket_servers", SERVER_ID);
    }
}
