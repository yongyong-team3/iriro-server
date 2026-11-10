package com.iriro.server.trail.presentation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TrailStatusResponse(Long trailId, String stationName, String dataSource, String dataStatus,
                                  String forecastModelVersion, String recoveryModelVersion,
                                  String waterStatus, BigDecimal currentWaterLevelMeters,
                                  OffsetDateTime observedAt, BigDecimal predictedWaterLevelMeters,
                                  OffsetDateTime forecastTargetAt, String trend, String recoveryStatus,
                                  Integer recoveryEtaAtLeastMinutes, String officialControlStatus,
                                  String notice, OffsetDateTime responseGeneratedAt) {
}
