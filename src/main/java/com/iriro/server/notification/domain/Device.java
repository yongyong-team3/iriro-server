package com.iriro.server.notification.domain;

import com.iriro.server.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "devices", uniqueConstraints = @UniqueConstraint(
        name = "uq_device_installation",
        columnNames = "installation_id"
))
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "installation_id", nullable = false, length = 36,
            columnDefinition = "CHAR(36) CHARACTER SET ascii COLLATE ascii_bin")
    private String installationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 12)
    private DevicePlatform platform;

    @Column(name = "expo_push_token", length = 255)
    private String expoPushToken;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Device() {
    }

    public Device(Member member, String installationId, DevicePlatform platform, String expoPushToken) {
        this.member = member;
        this.installationId = installationId;
        this.platform = platform;
        this.expoPushToken = expoPushToken;
    }

    public void updateRegistration(DevicePlatform platform, String expoPushToken) {
        this.platform = platform;
        this.expoPushToken = expoPushToken;
    }

    public void deactivatePushToken() {
        this.expoPushToken = null;
    }

    @PrePersist
    void setCreationTimestamps() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getInstallationId() { return installationId; }
    public DevicePlatform getPlatform() { return platform; }
    public String getExpoPushToken() { return expoPushToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
