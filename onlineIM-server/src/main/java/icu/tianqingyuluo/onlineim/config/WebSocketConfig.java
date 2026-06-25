package icu.tianqingyuluo.onlineim.config;

import org.springframework.context.annotation.Configuration;

/**
 * WebSocket配置类
 * Vert.x WebSocket相关组件通过@Component注解自动注册
 * 
 * 核心组件:
 * - VertxWebSocketServer: WebSocket服务器（位于websocket/server包）
 * - WebSocketAuthenticator: JWT认证器（位于websocket/handler包）
 * - WebSocketMessageRouter: 消息路由器（位于websocket/handler包）
 * - LocalSessionRegistry: 本地会话注册表（位于websocket/registry包）
 */
@Configuration
public class WebSocketConfig {
    // Vert.x WebSocket组件已通过@Component注解自动注册到Spring容器
    // 无需额外配置
}