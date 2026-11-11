package com.iriro.server.notification.application;

import com.iriro.server.trail.domain.Trail;
import com.iriro.server.trail.infrastructure.TrailRepository;
import com.iriro.server.trail.application.TrailStatusService;
import com.iriro.server.trail.presentation.TrailStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RecoveryEventPoller {
    private final TrailRepository trails;
    private final TrailStatusService statuses;
    private final RecoveryEventLifecycleService lifecycle;
    private final RecoveryNotificationService notifications;

    public RecoveryEventPoller(TrailRepository trails, TrailStatusService statuses,
                               RecoveryEventLifecycleService lifecycle, RecoveryNotificationService notifications) {
        this.trails = trails;
        this.statuses = statuses;
        this.lifecycle = lifecycle;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${iriro.notification.recovery-poll-ms:60000}",
            initialDelayString = "${iriro.notification.recovery-initial-delay-ms:60000}")
    public void poll() {
        try {
            notifications.checkPendingReceipts();
        } catch (Exception exception) {
            log.error("Expo push receipt check failed", exception);
        }
        for (Trail trail : trails.findAll()) {
            if (trail.getWaterStationCode() == null) continue;
            try {
                TrailStatusResponse status = statuses.status(trail);
                lifecycle.observe(status).ifPresent(eventId -> {
                    String title = trail.getName() + " 수위 회복 예측";
                    String body = trail.getName() + " 수위가 기준 아래로 내려갈 것으로 예측됩니다. 현장을 확인해 주세요.";
                    notifications.notifyRecoveryEvent(eventId, title, body);
                });
            } catch (Exception exception) {
                log.error("Recovery notification poll failed for trailId={}", trail.getId(), exception);
            }
        }
    }
}
