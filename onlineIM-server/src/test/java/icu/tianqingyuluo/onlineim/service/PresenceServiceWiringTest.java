package icu.tianqingyuluo.onlineim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.config.ServerIdentity;
import icu.tianqingyuluo.onlineim.websocket.registry.LocalSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 按 Spring 容器的方式创建 PresenceService：两个构造器时必须能选中带依赖的那个，
 * 并在会话注册表上登记下线回调。纯单测直接 new，发现不了容器里起不来的问题。
 */
class PresenceServiceWiringTest {

    @Test
    void springCanCreateThePresenceServiceAndRegistersTheDisconnectListener() {
        LocalSessionRegistry registry = mock(LocalSessionRegistry.class);
        new ApplicationContextRunner()
                // 与 Spring Boot 一致，让 "90s" 这类 @Value 能转成 Duration
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withBean(PresenceConnectionStore.class, () -> mock(PresenceConnectionStore.class))
                .withBean(PresenceAudienceService.class, () -> mock(PresenceAudienceService.class))
                .withBean(RedisStreamService.class, () -> mock(RedisStreamService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(LocalSessionRegistry.class, () -> registry)
                .withBean(ServerIdentity.class, () -> mock(ServerIdentity.class))
                .withBean(PresenceService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PresenceService.class);
                    verify(registry).addRemovalListener(any());
                });
    }
}
