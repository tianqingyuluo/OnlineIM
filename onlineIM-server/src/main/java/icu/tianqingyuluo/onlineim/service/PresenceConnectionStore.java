package icu.tianqingyuluo.onlineim.service;

import java.util.Set;

/**
 * 跨实例的在线连接登记。
 * 在线 = 该用户至少还有一条未过期的连接。
 * 上线 / 下线 / 过期清理必须原子，多实例同时操作时只有一次能观察到“0→1”或“1→0”。
 */
public interface PresenceConnectionStore {

    /** 连接已经不在，或这次没有把用户从在线集合里移除。 */
    int DISCONNECT_UNCHANGED = 0;
    /** 移除后该用户仍有其他连接。 */
    int DISCONNECT_STILL_ONLINE = 1;
    /** 这次移除使该用户变为离线，并已写入 last_seen。 */
    int DISCONNECT_WENT_OFFLINE = 2;

    /**
     * 登记一条连接。
     * @return 登记前仍然存活的连接数（不含本次）。0 表示这次让用户从离线变为在线。
     */
    int connect(String userId, String connectionId, long nowMs, long ttlMs);

    /**
     * 移除一条连接。
     * @return {@link #DISCONNECT_UNCHANGED}、{@link #DISCONNECT_STILL_ONLINE} 或 {@link #DISCONNECT_WENT_OFFLINE}
     */
    int disconnect(String userId, String connectionId, long nowMs);

    /**
     * 续期。连接键已不存在时返回 false，调用方应按新连接重新登记。
     */
    boolean heartbeat(String userId, String connectionId, long nowMs, long ttlMs);

    /** 去掉过期连接后的存活数量。 */
    int aliveCount(String userId, long nowMs);

    Long getLastSeen(String userId);

    void setHidden(String userId, boolean hidden);

    boolean isHidden(String userId);

    /** 当前被标记为在线的用户，给定时清理扫描。 */
    Set<String> onlineUserIds();

    /**
     * 若该用户已经没有任何存活连接，则从在线集合移除并写入 last_seen。
     * @return true 表示这次确实从在线变成了离线
     */
    boolean sweepOffline(String userId, long nowMs);

    /** 多实例清理互斥。拿到锁才扫描。 */
    boolean trySweepLock(String owner, long ttlMs);
}
