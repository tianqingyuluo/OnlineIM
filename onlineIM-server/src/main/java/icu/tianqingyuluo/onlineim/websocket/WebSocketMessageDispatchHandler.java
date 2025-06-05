package icu.tianqingyuluo.onlineim.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.service.RedisStreamService;
import icu.tianqingyuluo.onlineim.service.UserSessionService;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.MessageTypeSenderRegistry;
import icu.tianqingyuluo.onlineim.websocket.listener.handler.sender.MessageSenderHandler;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.Date;

/**
 * WebSocket消息处理器
 * 负责处理WebSocket连接建立后的消息交换
 */
@Slf4j
@ChannelHandler.Sharable
public class WebSocketMessageDispatchHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final RedisStreamService redisStreamService;
    private final MessageTypeSenderRegistry messageTypeSenderRegistry;
//    private final FriendWebSocketController friendWebSocketController;

    public WebSocketMessageDispatchHandler(JwtUtil jwtUtil,
                                          ObjectMapper objectMapper,
                                          RedisStreamService redisStreamService,
                                          MessageTypeSenderRegistry messageTypeSenderRegistry) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.redisStreamService = redisStreamService;
        this.messageTypeSenderRegistry = messageTypeSenderRegistry;
//        this.friendWebSocketController = friendWebSocketController;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 只处理文本消息
        if (frame instanceof TextWebSocketFrame) {
            String messageText = ((TextWebSocketFrame) frame).text();
            log.info("收到消息 {}",  messageText);
            String token = ctx.channel().attr(AttributeKey.valueOf("jwt_token")).get().toString();
            String userId = jwtUtil.getUserIDFromToken(token);
            String channelId = ctx.channel().id().asLongText();
            
            try {
                // 解析JSON消息
                JsonNode jsonNode = objectMapper.readTree(messageText);
                String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "UNKNOWN";
                String message = jsonNode.has("message") ? jsonNode.get("message").toString() : "";
//                String targetUserId = jsonNode.has("target_user_id") ? jsonNode.get("target_user_id").asText() : null;
                
                log.info("收到来自用户[{}]的消息: 类型={}, 内容={}",
                        userId, type, message);
                
                // 处理好友相关消息
                if (messageTypeSenderRegistry.supportMessageType(type)) {
                    // 处理其他类型的消息
                    MessageSenderHandler redisSender = messageTypeSenderRegistry.getHandler(type);
                    redisSender.publishMessage(message);
                } else {
                    log.warn("不支持的消息类型: {}", type);
                    ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"ERROR\",\"message\":\"不支持的消息类型\"}"));
                }
                
//                // 使用RedisStreamService发送消息到Redis Stream
//                if (targetUserId != null) {
//                    // 私聊消息
//                    redisStreamService.publishPrivateMessage(type, userId, message, channelId, targetUserId);
//                } else {
//                    // 创建Redis Stream事件（用于群聊或广播消息）
//                    RedisStreamEvent redisEvent = new RedisStreamEvent();
//                    redisEvent.setType(type);
//                    redisEvent.setSenderID(userId);
//                    redisEvent.setMessage(message);
//                    redisEvent.setReceiverIDs(Collections.emptyList());
//
//                    redisStreamService.publishMessage(redisEvent);
//                }
                
                // // 创建WebSocket消息事件（用于本地处理）
                // WebSocketMessageEvent wsEvent = WebSocketMessageEvent.builder()
                //         .type(type)
                //         .senderID(userId)
                //         .message(message)
                //         .senderChannelID(channelId)
                //         .build();
                
                // // 发布到Spring事件总线
                // applicationEventPublisher.publishEvent(wsEvent);
                
            } catch (JsonProcessingException e) {
                log.error("解析WebSocket消息失败: {}", e.getMessage());
                e.printStackTrace();
                ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"ERROR\",\"message\":\"\u6d88\u606f\u683c\u5f0f\u9519\u8bef\"}"));
            } catch (Exception e) {
                log.error("处理WebSocket消息异常: {}", e.getMessage(), e);
                ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"ERROR\",\"message\":\"\u670d\u52a1\u5668\u5185\u90e8\u9519\u8bef\"}"));
            }
        } else {
            // 不支持的消息类型
            log.warn("收到不支持的消息类型: {}", frame.getClass().getName());
            ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"ERROR\",\"message\":\"\u4e0d\u652f\u6301\u7684\u6d88\u606f\u7c7b\u578b\"}"));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket处理异常: {}", cause.getMessage(), cause);
        ctx.writeAndFlush(new TextWebSocketFrame("服务器发生内部错误"));
        ctx.close();
    }
}
