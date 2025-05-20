package icu.tianqingyuluo.onlineim.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import icu.tianqingyuluo.onlineim.websocket.event.TestEvent;
import icu.tianqingyuluo.onlineim.websocket.event.WebSocketMessageEvent;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket消息处理器
 * 负责处理WebSocket连接建立后的消息交换
 */
@Slf4j
@ChannelHandler.Sharable
public class WebSocketMessageDispatchHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final JwtUtil jwtUtil;

    private final ApplicationEventPublisher applicationEventPublisher;

    public WebSocketMessageDispatchHandler(JwtUtil jwtUtil, ApplicationEventPublisher applicationEventPublisher) {
        this.jwtUtil = jwtUtil;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 只处理文本消息
        if (frame instanceof TextWebSocketFrame) {
            String message = ((TextWebSocketFrame) frame).text();
            String token = ctx.channel().attr(AttributeKey.valueOf("jwt_token")).get().toString();
            String userID = jwtUtil.getUserIDFromToken(token);

            TestEvent event = new TestEvent(message);
            // 作为Spring事件推送
            applicationEventPublisher.publishEvent(event);

            log.info("收到来自用户[{}]的消息: {}", userID, message);

        } else {
            // 不支持的消息类型
            log.warn("收到不支持的消息类型: {}", frame.getClass().getName());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket处理异常: {}", cause.getMessage(), cause);
        ctx.writeAndFlush(new TextWebSocketFrame("服务器发生内部错误"));
        ctx.close();
    }
}
