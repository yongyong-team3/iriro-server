package com.iriro.server.river.presentation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HistoricalWaterStatusResponse(
        String stationCode, OffsetDateTime requestedAt, OffsetDateTime observedAt,
        BigDecimal currentWaterLevelMeters, String waterStatus,
        BigDecimal predictedWaterLevelMeters, OffsetDateTime forecastTargetAt, String trend,
        String recoveryStatus, Integer recoveryEtaAtLeastMinutes,
        OffsetDateTime aiEvaluatedAt, OffsetDateTime snapshotRecordedAt,
        String forecastModelVersion, String recoveryModelVersion,
        String dataSource, String dataStatus, String officialControlStatus) {
}
