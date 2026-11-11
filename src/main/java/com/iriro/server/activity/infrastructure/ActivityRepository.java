package com.iriro.server.activity.infrastructure;

import com.iriro.server.activity.domain.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    long countByMemberIdAndSimulatedFalse(Long memberId);

    @Query("SELECT COALESCE(CAST(SUM(a.distanceMeters) AS long), 0L) FROM Activity a WHERE a.memberId = :memberId AND a.simulated = false")
    long sumRealDistanceByMemberId(@Param("memberId") Long memberId);

    Optional<Activity> findByMemberIdAndIdempotencyKey(
            Long memberId,
            String idempotencyKey
    );

    // 활동 유형 필터 없이 조회
    Page<Activity> findByMemberIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long memberId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    // 활동 유형 필터 적용 조회
    Page<Activity> findByMemberIdAndActivityTypeAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long memberId,
            String activityType,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );
    Optional<Activity> findByIdAndMemberId(
            Long activityId,
            Long memberId
    );
}
