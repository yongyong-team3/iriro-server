package com.iriro.server.activity.infrastructure;

import com.iriro.server.activity.domain.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    long countByMemberIdAndSimulatedFalse(Long memberId);

    @Query("SELECT COALESCE(CAST(SUM(a.distanceMeters) AS long), 0L) FROM Activity a WHERE a.member.id = :memberId AND a.simulated = false")
    long sumRealDistanceByMemberId(@Param("memberId") Long memberId);

    List<Activity> findByMemberIdAndSimulatedFalse(Long memberId);

    Optional<Activity> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);
}
