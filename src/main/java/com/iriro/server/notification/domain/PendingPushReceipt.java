package com.iriro.server.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_push_receipts")
public class PendingPushReceipt {
    @Id
    @Column(name = "ticket_id", nullable = false, length = 128)
    private String ticketId;

    @Column(name = "device_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long deviceId;

    @Column(name = "push_token", nullable = false, length = 255)
    private String pushToken;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected PendingPushReceipt() { }

    public PendingPushReceipt(String ticketId, Long deviceId, String pushToken) {
        this.ticketId = ticketId;
        this.deviceId = deviceId;
        this.pushToken = pushToken;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) createdAt = LocalDateTime.now(Clock.systemUTC());
    }

    public String getTicketId() { return ticketId; }
    public Long getDeviceId() { return deviceId; }
    public String getPushToken() { return pushToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
