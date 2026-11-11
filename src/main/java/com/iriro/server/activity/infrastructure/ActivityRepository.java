package com.iriro.server.activity.infrastructure;

import com.iriro.server.activity.domain.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    long countByMemberIdAndSimulatedFalse(Long memberId);

    @Query("SELECT COALESCE(CAST(SUM(a.distanceMeters) AS long), 0L) FROM Activity a WHERE a.member.id = :memberId AND a.simulated = false")
    long sumRealDistanceByMemberId(@Param("memberId") Long memberId);

    List<Activity> findByMemberIdAndSimulatedFalse(Long memberId);

    Optional<Activity> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    Optional<Activity> findByIdAndMemberId(Long id, Long memberId);

    @Query("""
            select a from Activity a where a.member.id = :memberId
            and a.startedAt >= :start and a.startedAt < :end
            and (:activityType is null or a.activityType = :activityType)
            and (:includeSimulated = true or a.simulated = false)
            """)
    Page<Activity> findMonthPage(@Param("memberId") Long memberId, @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end, @Param("activityType") String activityType,
                                 @Param("includeSimulated") boolean includeSimulated, Pageable pageable);

    List<Activity> findByMemberIdAndSimulatedFalseAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDescIdDesc(
            Long memberId, LocalDateTime start, LocalDateTime end);
}
