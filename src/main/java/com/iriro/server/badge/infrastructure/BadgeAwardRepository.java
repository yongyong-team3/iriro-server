package com.iriro.server.badge.infrastructure;

import com.iriro.server.badge.domain.BadgeAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BadgeAwardRepository extends JpaRepository<BadgeAward, Long> {

    // 획득한 총 배지 수 집계
    long countByMemberId(Long memberId);

    // 특정 회원이 획득한 배지 획득 목록 전체 조회
    List<BadgeAward> findByMemberId(Long memberId);
}