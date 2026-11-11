package com.iriro.server.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "recovery_events")
public class RecoveryEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "trail_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long trailId;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime startedAt;

    @Column(name = "first_notified_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime firstNotifiedAt;

    @Column(name = "ended_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime endedAt;

    protected RecoveryEvent() { }

    public RecoveryEvent(Long trailId, LocalDateTime startedAt) {
        this.trailId = trailId;
        this.startedAt = startedAt;
    }

    public void end(LocalDateTime at) {
        if (endedAt == null && at.isAfter(startedAt)) endedAt = at;
    }

    public void markFirstNotified(LocalDateTime at) {
        if (firstNotifiedAt == null) firstNotifiedAt = at;
    }

    public Long getId() { return id; }
    public Long getTrailId() { return trailId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFirstNotifiedAt() { return firstNotifiedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
}
