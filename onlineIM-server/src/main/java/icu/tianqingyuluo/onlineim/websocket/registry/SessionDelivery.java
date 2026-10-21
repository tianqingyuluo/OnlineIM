package icu.tianqingyuluo.onlineim.websocket.registry;

import icu.tianqingyuluo.onlineim.websocket.session.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 把一条业务帧投递给某个用户当前在本节点上的全部连接。
 * {@code getAllByUserId} 返回 null 时退回单连接查询，方便既有单测桩只打桩 {@code getByUserId}。
 */
public final class SessionDelivery {

    private SessionDelivery() {
    }

    public static List<WebSocketSession> of(LocalSessionRegistry registry, String userId) {
        if (registry == null || userId == null) {
            return List.of();
        }
        List<WebSocketSession> all = registry.getAllByUserId(userId);
        // 真实注册表没有连接时返回空列表。Mockito 对 List 也默认返回空列表，
        // 这时再看 getByUserId，既有单连接测试不用改桩。
        if (all != null && !all.isEmpty()) {
            return active(all);
        }
        WebSocketSession one = registry.getByUserId(userId);
        if (one == null || !one.isActive()) {
            return List.of();
        }
        return List.of(one);
    }

    private static List<WebSocketSession> active(List<WebSocketSession> sessions) {
        List<WebSocketSession> result = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            if (session != null && session.isActive()) {
                result.add(session);
            }
        }
        return result;
    }
}
