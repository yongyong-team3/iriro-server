package com.iriro.server.activity.infrastructure;

import com.iriro.server.activity.domain.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 충돌 해결본
 *  - dev: 배지 계산·저장(멱등성)에 쓰는 메서드
 *  - 조회(ACT-02~05): 활동 목록·상세·주간·월간 통계에 쓰는 메서드
 *
 * 모든 조회 메서드의 memberId 는 Activity.member.id 로 해석된다.
 */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    // =====================================================================
    // dev
    // =====================================================================

    long countByMemberIdAndSimulatedFalse(Long memberId);

    @Query("SELECT COALESCE(CAST(SUM(a.distanceMeters) AS long), 0L) FROM Activity a WHERE a.member.id = :memberId AND a.simulated = false")
    long sumRealDistanceByMemberId(@Param("memberId") Long memberId);

    List<Activity> findByMemberIdAndSimulatedFalse(Long memberId);

    Optional<Activity> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    // =====================================================================
    // ACT-02 목록 (월별, 시연 기록 포함)
    // =====================================================================

    Page<Activity> findByMemberIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long memberId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    Page<Activity> findByMemberIdAndActivityTypeAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long memberId,
            String activityType,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    // =====================================================================
    // ACT-03 상세 (본인 기록만)
    // =====================================================================

    Optional<Activity> findByIdAndMemberId(Long id, Long memberId);

    // =====================================================================
    // ACT-04 주간 통계 (시연 기록 제외, 최신순)
    // =====================================================================

    List<Activity> findByMemberIdAndSimulatedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
            Long memberId,
            LocalDateTime start,
            LocalDateTime end
    );

    // =====================================================================
    // ACT-05 월간 통계 (시연 기록 제외)
    // =====================================================================

    List<Activity> findByMemberIdAndSimulatedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long memberId,
            LocalDateTime start,
            LocalDateTime end
    );
}