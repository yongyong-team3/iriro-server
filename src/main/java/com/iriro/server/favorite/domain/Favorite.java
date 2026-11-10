package com.iriro.server.favorite.domain;

import com.iriro.server.member.domain.Member;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

public class Favorite {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 완성 후 연결
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "route_id", nullable = false)
    // private Route route;
}
