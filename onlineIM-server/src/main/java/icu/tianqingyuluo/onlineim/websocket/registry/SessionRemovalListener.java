package icu.tianqingyuluo.onlineim.websocket.registry;

/**
 * 一条 WebSocket 连接从本节点注册表移除时回调（客户端关闭、异常或心跳超时）。
 */
@FunctionalInterface
public interface SessionRemovalListener {
    void onSessionRemoved(String userId, String connectionId);
}
