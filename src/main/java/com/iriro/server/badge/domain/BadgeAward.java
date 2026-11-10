package com.iriro.server.badge.domain;

import com.iriro.server.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "badge_awards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BadgeAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_code", nullable = false, length = 50)
    private BadgeCode badgeCode;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private LocalDateTime earnedAt;

    @Builder
    public BadgeAward(Member member, BadgeCode badgeCode) {
        this.member = member;
        this.badgeCode = badgeCode;
        this.earnedAt = LocalDateTime.now();
    }
}