package com.iriro.server.notification.application;

import com.iriro.server.notification.domain.RecoveryEvent;
import com.iriro.server.notification.infrastructure.RecoveryEventRepository;
import com.iriro.server.trail.infrastructure.TrailRepository;
import com.iriro.server.trail.presentation.TrailStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class RecoveryEventLifecycleService {
    private final TrailRepository trails;
    private final RecoveryEventRepository events;

    public RecoveryEventLifecycleService(TrailRepository trails, RecoveryEventRepository events) {
        this.trails = trails;
        this.events = events;
    }

    @Transactional
    public Optional<Long> observe(TrailStatusResponse status) {
        if (!"LIVE".equals(status.dataSource())
                || !("OK".equals(status.dataStatus()) || "PARTIAL".equals(status.dataStatus()))
                || status.observedAt() == null || status.currentWaterLevelMeters() == null) {
            return Optional.empty();
        }
        trails.findLockedById(status.trailId()).orElseThrow();
        Optional<RecoveryEvent> active = events.findFirstByTrailIdAndEndedAtIsNullOrderByStartedAtDesc(status.trailId());
        LocalDateTime observedAt = status.observedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        if ("NORMAL".equals(status.waterStatus())) {
            active.ifPresent(event -> event.end(observedAt));
            return Optional.empty();
        }
        if (!"HIGH_WATER".equals(status.waterStatus())) return Optional.empty();
        RecoveryEvent event = active.orElseGet(() -> events.saveAndFlush(new RecoveryEvent(status.trailId(), observedAt)));
        if (!"PREDICTED".equals(status.recoveryStatus())
                || status.recoveryEtaAtLeastMinutes() == null
                || status.recoveryEtaAtLeastMinutes() <= 0) {
            return Optional.empty();
        }
        return Optional.of(event.getId());
    }
}
