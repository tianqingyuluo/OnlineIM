package icu.tianqingyuluo.onlineim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
     * @return 配置好的虚拟线程执行器
     */
    @Bean(name="virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 启用虚拟线程
        executor.setTaskDecorator(runnable -> Thread.ofVirtual().start(runnable));
        return executor;
    }
}