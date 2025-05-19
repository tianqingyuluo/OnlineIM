package icu.tianqingyuluo.onlineim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler() {
            @Override
            public void initialize() {
                super.initialize();
                this.setThreadFactory(Thread.ofVirtual().factory());
            }
        };
        taskScheduler.setPoolSize(50); // 实际上是设置并发数量
        taskScheduler.setThreadNamePrefix("vt-taskScheduler-");
        return taskScheduler;
    }
}
