package icu.tianqingyuluo.onlineim.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket消息处理器
 * 负责处理WebSocket连接建立后的消息交换
 */
@Slf4j
@Component
public class WebSocketMessageHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    // 存储所有已连接的WebSocket通道，键为用户名，值为通道
    private static final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 只处理文本消息
        if (frame instanceof TextWebSocketFrame) {
            String message = ((TextWebSocketFrame) frame).text();
            String channelId = ctx.channel().id().asLongText();
            String username = WebSocketAuthHandler.getUsernameByChannelId(channelId);
            
            log.info("收到来自用户[{}]的消息: {}", username, message);
            
            // 这里可以添加消息处理逻辑，例如解析JSON、处理不同类型的消息等
            // 示例：简单地将消息回显给发送者
            ctx.channel().writeAndFlush(new TextWebSocketFrame("服务器已收到消息: " + message));
        } else {
            // 不支持的消息类型
            log.warn("收到不支持的消息类型: {}", frame.getClass().getName());
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asLongText();
        String username = WebSocketAuthHandler.getUsernameByChannelId(channelId);
        
        if (username != null) {
            // 将通道与用户名关联
            channels.put(username, ctx.channel());
            log.info("新的WebSocket连接建立: {}", username);
            
            // 发送欢迎消息
            ctx.channel().writeAndFlush(new TextWebSocketFrame("欢迎 " + username + " 连接到WebSocket服务器"));
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asLongText();
        String username = WebSocketAuthHandler.getUsernameByChannelId(channelId);
        
        if (username != null) {
            // 移除通道与用户名的关联
            channels.remove(username);
            log.info("WebSocket连接关闭: {}", username);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket处理异常: {}", cause.getMessage(), cause);
        ctx.close();
    }
    
    /**
     * 向指定用户发送消息
     */
    public static void sendMessageToUser(String username, String message) {
        Channel channel = channels.get(username);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(message));
        }
    }
    
    /**
     * 广播消息给所有已连接的用户
     */
    public static void broadcastMessage(String message) {
        channels.forEach((username, channel) -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(message));
            }
        });
    }
}