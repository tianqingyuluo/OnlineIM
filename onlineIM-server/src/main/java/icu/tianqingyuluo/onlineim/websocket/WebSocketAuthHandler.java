package icu.tianqingyuluo.onlineim.websocket;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import icu.tianqingyuluo.onlineim.util.LocalChannelRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * WebSocket认证处理器
 * 负责验证WebSocket连接请求中的JWT令牌
 */
@Slf4j
public class WebSocketAuthHandler extends ChannelInboundHandlerAdapter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper objectMapper;
    private final UserSessionService userSessionService;

    public WebSocketAuthHandler(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService, UserSessionService userSessionService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.objectMapper = new ObjectMapper();
        this.userSessionService = userSessionService;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 处理HTTP握手请求
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            
            // 获取请求头中的Authorization
            HttpHeaders headers = request.headers();
            String authHeader = headers.get(HttpHeaderNames.AUTHORIZATION);
            
            // 如果请求头中包含有效的JWT令牌，则立即验证
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (authenticateToken(token, ctx)) {
                    // 认证成功，继续处理请求
                    ctx.fireChannelRead(msg);
                    return;
                }
            } else {
                // 如果请求头中没有令牌，直接返回401。
                log.info("WebSocket没有携带JWT请求头");
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
                ctx.writeAndFlush(response);
                ctx.close();
                return;
            }
        }
        
        // 其他类型的消息，继续处理
        ctx.fireChannelRead(msg);
    }

    /**
     * 验证JWT令牌
     */
    private boolean authenticateToken(String token, ChannelHandlerContext ctx) {
        try {
            // 从令牌中获取用户名
            String username = jwtUtil.getUsernameFromToken(token);
            
            // 加载用户详情
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            // 验证令牌
            if (jwtUtil.validateToken(token, userDetails)) {
                // 认证成功，将用户信息与通道关联

                // 生成唯一的连接标识（方便横向扩展）
                String connectionID = "conn_" + IdUtil.simpleUUID();
                // 在本地表中构建映射
                LocalChannelRegistry.add(connectionID, ctx.channel().id().asLongText(), ctx.channel());
                // 在redis中存储路由信息
                userSessionService.storeDeviceSession(
                        token,
                        jwtUtil.getUserIDFromToken(token),
                        jwtUtil.getDeviceIDFromToken(token),
                        connectionID,
                        jwtUtil.getRemainingValidityTime(token),
                        TimeUnit.MILLISECONDS
                );
                // 往channel的属性里塞一个token方便注销的时候用
                ctx.channel().attr(AttributeKey.valueOf("jwt_token")).set(token);

                log.info("WebSocket认证成功: {}", username);
                return true;
            }
        } catch (Exception e) {
            log.error("JWT令牌验证失败: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接关闭时，移除用户信息
        String channelId = ctx.channel().id().asLongText();
        String token = ctx.channel().attr(AttributeKey.valueOf("jwt_token")).get().toString();
        String userID = jwtUtil.getUserIDFromToken(token);
        String deviceID = jwtUtil.getDeviceIDFromToken(token);
        // 删除该device在redis中的连接记录以及删除本地表中的channel对象
        userSessionService.removeDeviceSession(userID, deviceID, channelId);
        if (userID != null) {
            log.info("WebSocket连接关闭，用户: {}", userID);
        }
        super.channelInactive(ctx);
    }

}