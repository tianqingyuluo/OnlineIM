package icu.tianqingyuluo.onlineim.websocket.server;

import cn.hutool.core.util.IdUtil;
import icu.tianqingyuluo.onlineim.config.ServerIdentity;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.util.RealIPUtil;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketAuthenticator;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketAuthenticator.AuthResult;
import icu.tianqingyuluo.onlineim.websocket.handler.WebSocketMessageRouter;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Vert.x WebSocket服务器
 * 负责初始化和启动WebSocket服务，处理连接生命周期
 * 支持横向扩展，通过Redis注册服务实例
 */
@Slf4j
@Component
public class VertxWebSocketServer {

    @Value("${websocket.port:8081}")
    private int port;

    @Value("${websocket.path:/ws}")
    private String websocketPath;

    private Vertx vertx;
    private HttpServer httpServer;

    private final WebSocketAuthenticator authenticator;
    private final WebSocketMessageRouter messageRouter;
    private final LocalSessionRegistry sessionRegistry;
    private final UserSessionService userSessionService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ServerIdentity serverIdentity;

    public VertxWebSocketServer(WebSocketAuthenticator authenticator,
                                WebSocketMessageRouter messageRouter,
                                LocalSessionRegistry sessionRegistry,
                                UserSessionService userSessionService,
                                RedisTemplate<String, Object> redisTemplate,
                                ServerIdentity serverIdentity) {
        this.authenticator = authenticator;
        this.messageRouter = messageRouter;
        this.sessionRegistry = sessionRegistry;
        this.userSessionService = userSessionService;
        this.redisTemplate = redisTemplate;
        this.serverIdentity = serverIdentity;
    }

    /**
     * 启动WebSocket服务器
     */
    @PostConstruct
    public void start() {
        log.info("正在启动Vert.x WebSocket服务器，端口: {}, 路径: {}", port, websocketPath);

        try {
            // 创建Vert.x实例
            vertx = Vertx.vertx();

            // 创建路由器
            Router router = Router.router(vertx);

            // 健康检查端点
            router.get("/health").handler(ctx -> {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end("{\"status\":\"UP\",\"connections\":" + sessionRegistry.getOnlineCount() + "}");
            });

            // 配置HTTP服务器选项
            HttpServerOptions options = new HttpServerOptions()
                    .setPort(port)
                    .setHost("0.0.0.0")
                    .setTcpKeepAlive(true);

            // 创建并启动HTTP服务器
            // 使用CountDownLatch阻塞等待listen()完成，确保端口绑定失败时中断Spring启动，
            // 避免部署呈现健康状态而实时消息服务实际不可用的情况
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();

            vertx.createHttpServer(options)
                    .requestHandler(router)
                    .webSocketHandler(ws -> handleWebSocketConnection(ws, ws.uri()))
                    .listen()
                    .onSuccess(server -> {
                        httpServer = server;
                        log.info("Vert.x WebSocket服务器启动成功，监听端口: {}", server.actualPort());
                        latch.countDown();
                    })
                    .onFailure(err -> {
                        failure.set(err);
                        log.error("Vert.x WebSocket服务器启动失败: {}", err.getMessage(), err);
                        latch.countDown();
                    });

            latch.await();
            if (failure.get() != null) {
                throw new RuntimeException("WebSocket服务器启动失败", failure.get());
            }

            // 向Redis注册服务
            registerServiceToRedis();

        } catch (Exception e) {
            log.error("WebSocket服务器启动异常: {}", e.getMessage(), e);
            throw new RuntimeException("WebSocket服务器启动失败", e);
        }
    }

    /**
     * 处理WebSocket连接
     * @param ws ServerWebSocket对象
     * @param uri 请求URI
     */
    private void handleWebSocketConnection(ServerWebSocket ws, String uri) {
        String requestPath = uri == null ? "" : uri.split("\\?", 2)[0];
        if (!websocketPath.equals(requestPath)) {
            log.warn("WebSocket连接被拒绝: 非法路径 {}", requestPath);
            ws.reject(404);
            return;
        }

        // 从URI中提取token并进行认证
        String token = authenticator.extractTokenFromUri(uri);
        
        if (token == null) {
            log.warn("WebSocket连接被拒绝: 缺少认证令牌");
            ws.reject(401);
            return;
        }

        // 进行JWT认证
        AuthResult authResult = authenticator.authenticate(token);
        
        if (!authResult.isSuccess()) {
            log.warn("WebSocket认证失败: {}", authResult.getFailureReason());
            ws.reject(401);
            return;
        }

        // 认证成功，接受连接
        ws.accept();

        // 生成连接ID并创建会话
        String connectionId = "conn_" + IdUtil.simpleUUID();
        WebSocketSession session = WebSocketSession.builder()
                .connectionId(connectionId)
                .userId(authResult.getUserId())
                .deviceId(authResult.getDeviceId())
                .token(authResult.getToken())
                .socket(ws)
                .build();

        // 注册到本地会话注册表
        sessionRegistry.register(session);

        // 在Redis中存储路由信息（用于横向扩展）
        try {
            userSessionService.storeDeviceSession(
                    authResult.getToken(),
                    authResult.getUserId(),
                    authResult.getDeviceId(),
                    connectionId,
                    authResult.getRemainingTimeMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (Exception e) {
            log.error("存储设备会话到Redis失败: {}", e.getMessage());
        }

        log.info("WebSocket连接建立成功: connectionId={}, userId={}", connectionId, authResult.getUserId());

        // 设置消息处理器
        ws.textMessageHandler(message -> {
            messageRouter.route(session, message);
        });

        // 设置连接关闭处理器
        ws.closeHandler(v -> {
            handleWebSocketClose(session);
        });

        // 设置异常处理器
        ws.exceptionHandler(err -> {
            log.error("WebSocket异常: connectionId={}, error={}", connectionId, err.getMessage());
            handleWebSocketClose(session);
        });
    }

    /**
     * 处理WebSocket连接关闭
     * @param session WebSocket会话
     */
    private void handleWebSocketClose(WebSocketSession session) {
        if (session == null) {
            return;
        }

        String connectionId = session.getConnectionId();
        String userId = session.getUserId();
        String deviceId = session.getDeviceId();
        String socketId = session.getSocketId();

        // 从本地注册表注销
        sessionRegistry.unregister(connectionId);

        // 从Redis中删除会话信息
        try {
            userSessionService.removeDeviceSession(userId, deviceId, socketId);
        } catch (Exception e) {
            log.error("从Redis删除设备会话失败: {}", e.getMessage());
        }

        log.info("WebSocket连接已关闭: connectionId={}, userId={}", connectionId, userId);
    }

    /**
     * 关闭WebSocket服务器
     */
    @PreDestroy
    public void stop() {
        log.info("正在关闭Vert.x WebSocket服务器...");

        // 销毁本实例的 Redis Stream 消费者组（避免孤儿 group）
        destroyConsumerGroup();

        // 从Redis注销服务
        unregisterServiceFromRedis();

        // 关闭HTTP服务器
        if (httpServer != null) {
            httpServer.close()
                    .onSuccess(v -> log.info("HTTP服务器已关闭"))
                    .onFailure(err -> log.error("关闭HTTP服务器失败: {}", err.getMessage()));
        }

        // 关闭Vert.x实例
        if (vertx != null) {
            vertx.close()
                    .onSuccess(v -> log.info("Vert.x实例已关闭"))
                    .onFailure(err -> log.error("关闭Vert.x实例失败: {}", err.getMessage()));
        }

        log.info("Vert.x WebSocket服务器已关闭");
    }

    /**
     * 向Redis注册当前服务实例
     */
    private void registerServiceToRedis() {
        try {
            String serverId = serverIdentity.getServerId();
            String serverInfo = "{\"ip\":\"" + RealIPUtil.getRealLocalIP() + "\",\"port\":" + port + "}";
            redisTemplate.opsForHash().put("websocket_servers", serverId, serverInfo);
            log.info("服务已注册到Redis: serverId={}", serverId);
        } catch (Exception e) {
            log.error("服务注册到Redis失败: {}", e.getMessage());
        }
    }

    /**
     * 从Redis注销当前服务实例
     */
    private void unregisterServiceFromRedis() {
        String serverId = serverIdentity.getServerId();
        try {
            redisTemplate.opsForHash().delete("websocket_servers", serverId);
            log.info("服务已从Redis注销: serverId={}", serverId);
        } catch (Exception e) {
            log.error("从Redis注销服务失败: {}", e.getMessage());
        }
    }

    /**
     * 销毁本实例的 Redis Stream 消费者组
     * 正常停机时调用，避免 Redis 残留孤儿 group；异常崩溃残留的 group 清理不在本任务范围
     */
    private void destroyConsumerGroup() {
        String groupId = serverIdentity.getGroupId();
        try {
            redisTemplate.opsForStream().destroyGroup("im:message:stream", groupId);
            log.info("已销毁消费者组: {}", groupId);
        } catch (Exception e) {
            log.warn("销毁消费者组失败: groupId={}, err={}", groupId, e.getMessage());
        }
    }

    /**
     * 获取当前在线连接数
     * @return 连接数量
     */
    public int getOnlineCount() {
        return sessionRegistry.getOnlineCount();
    }
}
