package com.iriro.server.auth.domain;

import com.iriro.server.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_sessions", uniqueConstraints = @UniqueConstraint(
        name = "uq_refresh_token_hash",
        columnNames = "token_hash"
))
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "token_hash", nullable = false, length = 64,
            columnDefinition = "CHAR(64) CHARACTER SET ascii COLLATE ascii_bin")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected RefreshSession() {
    }

    public RefreshSession(Member member, String tokenHash, LocalDateTime expiresAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(Clock.systemUTC());
        }
    }

    public void revoke(LocalDateTime at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
