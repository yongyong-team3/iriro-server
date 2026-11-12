package com.iriro.server.river.infrastructure;

import com.iriro.server.river.domain.HistoricalStatusSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface HistoricalStatusSnapshotRepository extends JpaRepository<HistoricalStatusSnapshot, Long> {
    @Query(value = """
            SELECT * FROM historical_status_snapshots
            WHERE station_code = :stationCode
              AND data_source = 'LIVE'
              AND observed_at BETWEEN :oldestObservedAt AND :requestedAt
              AND ai_evaluated_at <= :requestedAt
              AND snapshot_recorded_at <= :requestedAt
            ORDER BY observed_at DESC, ai_evaluated_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<HistoricalStatusSnapshot> findLatestAvailable(
            @Param("stationCode") String stationCode,
            @Param("oldestObservedAt") LocalDateTime oldestObservedAt,
            @Param("requestedAt") LocalDateTime requestedAt);
}
