package icu.tianqingyuluo.onlineim.scheduler;

import icu.tianqingyuluo.onlineim.service.call.CallSignalingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 领取超时未接听的通话。多实例通过 Redis 原子领取，不会重复落库。
 */
@Slf4j
@Component
public class CallTimeoutScheduler {

    private final CallSignalingService callSignalingService;

    public CallTimeoutScheduler(CallSignalingService callSignalingService) {
        this.callSignalingService = callSignalingService;
    }

    @Scheduled(fixedDelayString = "${im.webrtc.call-sweep-interval-ms:1000}")
    public void sweep() {
        try {
            callSignalingService.expireDueCalls();
        } catch (Exception ex) {
            log.warn("清理超时通话失败: {}", ex.getMessage());
        }
    }
}
