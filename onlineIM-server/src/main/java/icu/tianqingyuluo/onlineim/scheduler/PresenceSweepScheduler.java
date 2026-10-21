package icu.tianqingyuluo.onlineim.scheduler;

import icu.tianqingyuluo.onlineim.service.PresenceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 连接键过期后（心跳停止或实例崩溃）把用户收成离线。多实例靠 Redis 锁保证只有一个在扫。
 */
@Component
public class PresenceSweepScheduler {

    private final PresenceService presenceService;

    public PresenceSweepScheduler(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @Scheduled(fixedDelayString = "${presence.sweep-interval-ms:30000}")
    public void sweep() {
        presenceService.sweepExpired();
    }
}
