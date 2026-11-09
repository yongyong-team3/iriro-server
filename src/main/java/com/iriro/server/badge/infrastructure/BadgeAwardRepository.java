package com.iriro.server.badge.infrastructure;

import com.iriro.server.badge.domain.BadgeAward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeAwardRepository extends JpaRepository<BadgeAward, Long> {

    // 획득한 총 배지 수 집계
    long countByMemberId(Long memberId);
}