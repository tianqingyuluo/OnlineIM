package icu.tianqingyuluo.onlineim.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Netty WebSocket 服务器
 * 负责初始化和启动WebSocket服务
 */
@Slf4j
public class NettyWebSocketServer {

    @Value("${websocket.port:8081}")
    private int port;

    @Value("${websocket.path:/ws}")
    private String websocketPath;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    private final WebSocketAuthHandler webSocketAuthHandler;
    private final WebSocketMessageDispatchHandler webSocketMessageDispatchHandler;

    public NettyWebSocketServer(WebSocketAuthHandler webSocketAuthHandler, 
                               WebSocketMessageDispatchHandler webSocketMessageDispatchHandler) {
        this.webSocketAuthHandler = webSocketAuthHandler;
        this.webSocketMessageDispatchHandler = webSocketMessageDispatchHandler;
    }

    /**
     * 启动WebSocket服务器
     */
    @PostConstruct
    public void start() throws InterruptedException {
        log.info("正在启动WebSocket服务器，端口: {}, 路径: {}", port, websocketPath);
        
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(
                                    // HTTP编解码器
                                    new HttpServerCodec())
                                    // 支持大数据流
                                    .addLast(new ChunkedWriteHandler())
                                    // 聚合HTTP消息
                                    .addLast(new HttpObjectAggregator(65536))
                                    // JWT认证处理器
                                    .addLast(webSocketAuthHandler)
                                    // WebSocket协议处理
                                    .addLast(new WebSocketServerProtocolHandler(websocketPath, null, true))
                                    // 至此之后完成协议升级，开始websocket连接生命周期
                                    // 自定义消息处理器
                                    .addLast(webSocketMessageDispatchHandler);
                        }
                    });

            // 绑定端口并启动服务器
            channelFuture = bootstrap.bind(port).sync();
            log.info("WebSocket服务器启动成功，监听端口: {}", port);

        } catch (Exception e) {
            log.error("WebSocket服务器启动失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 关闭WebSocket服务器
     */
    @PreDestroy
    public void stop() {
        log.info("正在关闭WebSocket服务器...");
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("WebSocket服务器已关闭");
    }
}