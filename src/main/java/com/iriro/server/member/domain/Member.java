package com.iriro.server.member.domain;

import jakarta.persistence.*;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_members_name_key",
                columnNames = "member_name_key"
        )
)
(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED"
    private Long id;

    @Column(name = "member_name", length = 12)
    private String memberName;

    @Column(name = "member_name_key", length = 12, columnDefinition = "VARCHAR(12) COLLATE utf8mb4_bin")
    private String memberNameKey;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Member() {
    }

    public Member(String memberName, String memberNameKey) {
        this.memberName = memberName;
        this.memberNameKey = memberNameKey;
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

    public Long getId() {
        return id;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getMemberNameKey() {
        return memberNameKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}