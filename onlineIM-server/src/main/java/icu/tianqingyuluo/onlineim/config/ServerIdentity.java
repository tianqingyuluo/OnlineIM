package icu.tianqingyuluo.onlineim.config;

import cn.hutool.core.util.IdUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 当前服务实例的身份标识
 * 统一管理 serverId 的生成与派生信息（如 consumer group name）
 * 替代 VertxWebSocketServer 内部私有的 serverId 字段，供消费侧复用
 */
@Component
public class ServerIdentity {

    private static final String SERVER_ID_PREFIX = "serverID_";
    private static final String GROUP_ID_PREFIX = "im-msg-grp-";

    private String serverId;
    private String groupId;

    @PostConstruct
    public void init() {
        this.serverId = SERVER_ID_PREFIX + IdUtil.simpleUUID();
        this.groupId = GROUP_ID_PREFIX + serverId;
    }

    public String getServerId() {
        return serverId;
    }

    /**
     * 派生 Redis Stream 消费者组名，绑定本实例的 serverId
     * 每次启动生成新 group（serverId 非持久化），正常停机时由调用方销毁
     */
    public String getGroupId() {
        return groupId;
    }
}
