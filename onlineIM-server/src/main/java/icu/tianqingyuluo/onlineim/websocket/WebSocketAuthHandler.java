package icu.tianqingyuluo.onlineim.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket认证处理器
 * 负责验证WebSocket连接请求中的JWT令牌
 */
@Slf4j
@Component
public class WebSocketAuthHandler extends ChannelInboundHandlerAdapter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper objectMapper;
    
    // 存储已认证的用户信息，键为ChannelId，值为用户名
    private static final ConcurrentHashMap<String, String> authenticatedUsers = new ConcurrentHashMap<>();

    public WebSocketAuthHandler(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.objectMapper = new ObjectMapper();
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
                // 如果请求头中没有令牌，允许连接建立，后续通过WebSocket消息进行认证
                log.info("WebSocket连接请求，等待认证消息");
                ctx.fireChannelRead(msg);
                return;
            }
            
            // 认证失败，返回401响应
            log.warn("WebSocket认证失败，未提供有效的JWT令牌");
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
            ctx.writeAndFlush(response);
            ctx.close();
            return;
        }
        
        // 处理WebSocket文本消息中的认证
        if (msg instanceof TextWebSocketFrame) {
            TextWebSocketFrame frame = (TextWebSocketFrame) msg;
            String channelId = ctx.channel().id().asLongText();
            
            // 如果用户已认证，直接传递消息
            if (authenticatedUsers.containsKey(channelId)) {
                ctx.fireChannelRead(msg);
                return;
            }
            
            // 尝试从消息中解析认证信息
            try {
                JsonNode jsonNode = objectMapper.readTree(frame.text());
                if (jsonNode.has("type") && "AUTH".equals(jsonNode.get("type").asText()) 
                        && jsonNode.has("token")) {
                    String token = jsonNode.get("token").asText();
                    
                    if (authenticateToken(token, ctx)) {
                        // 认证成功，发送成功消息
                        String username = authenticatedUsers.get(channelId);
                        ctx.channel().writeAndFlush(new TextWebSocketFrame(
                                "{\"type\":\"AUTH_SUCCESS\",\"username\":\"" + username + "\"}"));
                        return;
                    } else {
                        // 认证失败，关闭连接
                        ctx.channel().writeAndFlush(new TextWebSocketFrame(
                                "{\"type\":\"AUTH_FAILED\",\"message\":\"无效的令牌\"}"));
                        ctx.close();
                        return;
                    }
                }
            } catch (Exception e) {
                log.error("解析认证消息失败: {}", e.getMessage());
            }
            
            // 未认证的消息，要求认证
            ctx.channel().writeAndFlush(new TextWebSocketFrame(
                    "{\"type\":\"REQUIRE_AUTH\",\"message\":\"请先进行认证\"}"));
            return;
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
                authenticatedUsers.put(ctx.channel().id().asLongText(), username);
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
        String username = authenticatedUsers.remove(channelId);
        if (username != null) {
            log.info("WebSocket连接关闭，用户: {}", username);
        }
        super.channelInactive(ctx);
    }
    
    /**
     * 获取通道关联的用户名
     */
    public static String getUsernameByChannelId(String channelId) {
        return authenticatedUsers.get(channelId);
    }
}