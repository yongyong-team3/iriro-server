package com.iriro.server.badge.domain;

import com.iriro.server.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "badge_awards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(BadgeAwardId.class)
public class BadgeAward {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Id
    @Column(name = "badge_code", nullable = false, length = 32)
    private String badgeCode;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private LocalDateTime earnedAt;
}