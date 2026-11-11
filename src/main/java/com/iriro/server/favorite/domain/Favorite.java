package com.iriro.server.favorite.domain;

import com.iriro.server.member.domain.Member;
import com.iriro.server.trail.domain.Route;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "favorites",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_favorites_member_route",
                        columnNames = {"member_id", "route_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Favorite(Member member, Route route) {
        this.member = member;
        this.route = route;
        this.createdAt = LocalDateTime.now();
    }
}