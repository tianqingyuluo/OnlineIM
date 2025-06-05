package icu.tianqingyuluo.onlineim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置类
 * 用于配置异步任务相关的线程池和执行器
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    /**
     * 创建虚拟线程执行器
     */
    @Bean
    public VirtualThreadTaskExecutor virtualThreadTaskExecutor() {
        return new VirtualThreadTaskExecutor("event-vt-"); // 线程名前缀
    }
}