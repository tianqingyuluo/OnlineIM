package icu.tianqingyuluo.onlineim.config;

import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import icu.tianqingyuluo.onlineim.websocket.NettyWebSocketServer;
import icu.tianqingyuluo.onlineim.websocket.WebSocketAuthHandler;
import icu.tianqingyuluo.onlineim.websocket.WebSocketMessageDispatchHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket配置类
 * 用于配置WebSocket相关的Bean和设置
 */
@Configuration
public class WebSocketConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtUtil jwtUtil;

    public WebSocketConfig(UserDetailsServiceImpl userDetailsService, JwtUtil jwtUtil) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 注册WebSocket认证处理器
     */
    @Bean
    public WebSocketAuthHandler webSocketAuthHandler() {
        return new WebSocketAuthHandler(jwtUtil, userDetailsService);
    }

    /**
     * 注册WebSocket消息处理器
     */
    @Bean
    public WebSocketMessageDispatchHandler webSocketMessageHandler() {
        return new WebSocketMessageDispatchHandler();
    }

    /**
     * 注册WebSocket服务器
     */
    @Bean
    public NettyWebSocketServer nettyWebSocketServer(WebSocketAuthHandler webSocketAuthHandler, 
                                                    WebSocketMessageDispatchHandler webSocketMessageDispatchHandler) {
        return new NettyWebSocketServer(webSocketAuthHandler, webSocketMessageDispatchHandler);
    }
}