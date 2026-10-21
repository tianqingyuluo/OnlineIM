package icu.tianqingyuluo.onlineim.service.impl;

import com.redis.testcontainers.RedisContainer;
import icu.tianqingyuluo.onlineim.service.PresenceConnectionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用真实 Redis 核对 Lua：多连接聚合、最后一条才离线、过期后清理只触发一次。
 * 没有 Docker 时跳过；同一套语义由 InMemoryPresenceConnectionStore + PresenceServiceTest 始终覆盖。
 */
class RedisPresenceConnectionStoreTest {

    @Test
    void lastConnectionCloseAndExpiryMatchTheInMemoryContract() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "需要 Docker 才能起 Redis");
        try (RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"))) {
            redis.start();
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            RedisPresenceConnectionStore store = new RedisPresenceConnectionStore(template);
            try {
                assertLifecycle(store);
            } finally {
                factory.destroy();
            }
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void assertLifecycle(RedisPresenceConnectionStore store) throws Exception {
        long now = 1_700_000_000_000L;
        assertEquals(0, store.connect("usr_a", "conn_1", now, 90_000));
        assertEquals(1, store.connect("usr_a", "conn_2", now, 90_000));
        assertEquals(PresenceConnectionStore.DISCONNECT_STILL_ONLINE, store.disconnect("usr_a", "conn_1", now + 1));
        assertEquals(PresenceConnectionStore.DISCONNECT_WENT_OFFLINE, store.disconnect("usr_a", "conn_2", now + 2));
        assertEquals(now + 2, store.getLastSeen("usr_a"));
        assertEquals(PresenceConnectionStore.DISCONNECT_UNCHANGED, store.disconnect("usr_a", "conn_2", now + 3));

        assertEquals(0, store.connect("usr_b", "conn_b", now, 50));
        assertTrue(store.heartbeat("usr_b", "conn_b", now + 10, 50));
        Thread.sleep(200);
        assertFalse(store.heartbeat("usr_b", "conn_b", now + 80, 50));
        assertTrue(store.sweepOffline("usr_b", now + 80));
        assertFalse(store.sweepOffline("usr_b", now + 80));
        assertEquals(now + 80, store.getLastSeen("usr_b"));

        store.setHidden("usr_a", true);
        assertTrue(store.isHidden("usr_a"));
        store.setHidden("usr_a", false);
        assertFalse(store.isHidden("usr_a"));
    }
}
