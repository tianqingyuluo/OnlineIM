package icu.tianqingyuluo.onlineim.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import icu.tianqingyuluo.onlineim.websocket.listener.RedisEventListener;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;

@EnableCaching
@Configuration
@Slf4j
public class RedisConfig implements CachingConfigurer {

    // 默认缓存过期时间: 1天
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofDays(1);

    /**
     * 自定义缓存键生成器
     */
    @Override
    @Bean
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName()); // 使用简单类名
            sb.append(":").append(method.getName()); // 方法名
            if (params.length > 0) {
                sb.append(":").append(Arrays.deepHashCode(params)); // 参数哈希
            }
            return sb.toString();
        };
    }

    /**
     * 配置Redis缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_CACHE_TTL)
                .disableCachingNullValues() // 不缓存null值
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware() // 支持事务
                .build();
    }

    /**
     * 配置Redis操作模板
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用String序列化器作为key的序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 使用GenericJackson2JsonRedisSerializer作为value的序列化器
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamContainer(
            RedisConnectionFactory connectionFactory, RedisEventListener redisEventListener,
            RedisTemplate<String, Object> redisTemplate, ServerIdentity serverIdentity) {
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .targetType(String.class)
                        .build();
        StreamMessageListenerContainer<String, ObjectRecord<String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        String streamKey = "im:message:stream";
        String groupId = serverIdentity.getGroupId();
        String consumerName = serverIdentity.getServerId();

        // 启动时创建本实例的消费者组（从流尾开始，不重放历史；已存在则忽略 BUSYGROUP）
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("$"), groupId);
            log.info("已创建 Redis Stream 消费者组: stream={}, group={}", streamKey, groupId);
        } catch (Exception e) {
            log.warn("创建消费者组失败（可能已存在）: stream={}, group={}, err={}", streamKey, groupId, e.getMessage());
        }

        // 以消费者组模式注册监听器：每实例各读全量流（广播），group 记住 last-delivered-id 避免重启重放
        container.receive(
            Consumer.from(groupId, consumerName),
            StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
            redisEventListener
        );

        container.start();

        return container;
    }
}