package com.iriro.server.badge.infrastructure;

import com.iriro.server.badge.domain.BadgeAward;
import com.iriro.server.badge.domain.BadgeAwardId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BadgeAwardRepository extends JpaRepository<BadgeAward, BadgeAwardId> {

    // 획득한 총 배지 수 집계
    long countByMemberId(Long memberId);

    // 특정 회원이 획득한 배지 획득 목록 전체 조회
    @Query("SELECT b FROM BadgeAward b WHERE b.member.id = :memberId")
    List<BadgeAward> findByMemberId(@Param("memberId") Long memberId);
}
