package com.iriro.server.auth.domain;

import com.iriro.server.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "email_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_email_credentials_email",
                columnNames = "email_normalized"
        )
)
public class EmailCredential {

    @Id
    @Column(name = "member_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long memberId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_email_credentials_member")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Member member;

    @Column(
            name = "email_normalized",
            nullable = false,
            length = 254,
            columnDefinition = "VARCHAR(254) COLLATE utf8mb4_bin"
    )
    private String emailNormalized;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected EmailCredential() {
    }

    public EmailCredential(Member member, String emailNormalized, String passwordHash) {
        this.member = member;
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
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

    public Long getMemberId() {
        return memberId;
    }

    public Member getMember() {
        return member;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
