package icu.tianqingyuluo.onlineim.util;

import io.netty.channel.Channel;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.ConcurrentMap;

public class LocalChannelRegistry {

    // 使用Netty实现的并发map，用于存储connectionID和channel之间的映射
    public static final ConcurrentMap<String, Channel> CHANNEL_MAP = PlatformDependent.newConcurrentHashMap();
    // 使用Netty实现的并发map，用于存储connectionID和channelID之间的映射
    public static final ConcurrentMap<String, String> CONNECTION_ID_MAP = PlatformDependent.newConcurrentHashMap();

    // 注册Channel
    public static void add(String connectionID, String channelID, Channel channel) {
        // connectionI为全局唯一ID，用于横向拓展
        CHANNEL_MAP.put(connectionID, channel);
        CONNECTION_ID_MAP.put(channelID, connectionID);
        // 监听channel的关闭
        channel.closeFuture().addListener(future -> remove(channelID));
    }

    // 查找channel
    public static Channel get(String connectionID) {
        Channel ch = CHANNEL_MAP.get(connectionID);
        // 过滤无效连接
        return (ch != null && ch.isActive()) ? ch : null;
    }

    // 移除channel
    public static void remove(String channelID) {
        String connectionID = CONNECTION_ID_MAP.get(channelID);
        CHANNEL_MAP.remove(connectionID);
        CONNECTION_ID_MAP.remove(channelID);
    }
}
