package com.iriro.server.notification.domain;

import com.iriro.server.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_settings")
public class NotificationSetting {

    @Id
    @Column(name = "member_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "recovery_forecast_enabled", nullable = false)
    private boolean recoveryForecastEnabled;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected NotificationSetting() {
    }

    public NotificationSetting(Member member, boolean recoveryForecastEnabled) {
        this.member = member;
        this.recoveryForecastEnabled = recoveryForecastEnabled;
    }

    public void updateRecoveryForecastEnabled(boolean enabled) {
        this.recoveryForecastEnabled = enabled;
    }

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    public Long getMemberId() { return memberId; }
    public Member getMember() { return member; }
    public boolean isRecoveryForecastEnabled() { return recoveryForecastEnabled; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
