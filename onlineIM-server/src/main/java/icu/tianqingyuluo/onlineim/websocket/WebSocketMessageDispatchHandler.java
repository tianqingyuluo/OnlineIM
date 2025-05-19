package icu.tianqingyuluo.onlineim.websocket;

import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket消息处理器
 * 负责处理WebSocket连接建立后的消息交换
 */
@Slf4j
public class WebSocketMessageDispatchHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final JwtUtil jwtUtil;

    public WebSocketMessageDispatchHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 只处理文本消息
        if (frame instanceof TextWebSocketFrame) {
            String message = ((TextWebSocketFrame) frame).text();
            String token = ctx.channel().attr(AttributeKey.valueOf("jwt_token")).get().toString();
            String userID = jwtUtil.getUserIDFromToken(token);
            
            log.info("收到来自用户[{}]的消息: {}", userID, message);
            
            // 这里可以添加消息处理逻辑，例如解析JSON、处理不同类型的消息等
            // 示例：简单地将消息回显给发送者
            ctx.channel().writeAndFlush(new TextWebSocketFrame("服务器已收到消息: " + message));
        } else {
            // 不支持的消息类型
            log.warn("收到不支持的消息类型: {}", frame.getClass().getName());
        }
    }

//    @Override
//    public void handlerAdded(ChannelHandlerContext ctx) {
//        String token = ctx.channel().attr(AttributeKey.valueOf("jwt_token")).get().toString();
//        String userID = jwtUtil.getUserIDFromToken(token);
//
//        if (userID != null) {
//            // 将通道与用户名关联
//            channels.put(userID, ctx.channel());
//            log.info("新的WebSocket连接建立: {}", userID);
//
//            // 发送欢迎消息
//            ctx.channel().writeAndFlush(new TextWebSocketFrame("欢迎 " + userID + " 连接到WebSocket服务器"));
//        }
//    }
//
//    @Override
//    public void handlerRemoved(ChannelHandlerContext ctx) {
//        String token = ctx.channel().attr(AttributeKey.valueOf("jwt_token")).get().toString();
//        String userID = jwtUtil.getUserIDFromToken(token);
//
//        if (userID != null) {
//            // 移除通道与用户名的关联
//            channels.remove(userID);
//            log.info("WebSocket连接关闭: {}", userID);
//        }
//    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket处理异常: {}", cause.getMessage(), cause);
        ctx.writeAndFlush(new TextWebSocketFrame("服务器发生内部错误"));
        ctx.close();
    }
    
//    /**
//     * 向指定用户发送消息
//     */
//    public static void sendMessageToUser(String userID, String message) {
//        Channel channel =
//        if (channel != null && channel.isActive()) {
//            channel.writeAndFlush(new TextWebSocketFrame(message));
//        }
//    }
//
//    /**
//     * 广播消息给所有已连接的用户
//     */
//    public static void broadcastMessage(String message) {
//        channels.forEach((username, channel) -> {
//            if (channel.isActive()) {
//                channel.writeAndFlush(new TextWebSocketFrame(message));
//            }
//        });
//    }
}