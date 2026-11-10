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

    @EmbeddedId
    private BadgeAwardId id;

    @MapsId("memberId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private LocalDateTime earnedAt;

    @Builder
    public BadgeAward(Member member, BadgeCode badgeCode) {
        this.id = new BadgeAwardId(member.getId(), badgeCode);
        this.member = member;
        this.earnedAt = LocalDateTime.now();
    }

    public BadgeCode getBadgeCode() {
        return id.getBadgeCode();
    }
}