package com.iriro.server.activity.presentation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ActivityDetailResponse(Long activityId, Long routeId, String routeName,
                                     String activityType, OffsetDateTime startedAt, OffsetDateTime endedAt,
                                     BigDecimal distanceMeters, int elapsedSeconds, boolean simulated,
                                     long averagePaceSecondsPerKm, Geometry geometry) {
    public record Geometry(String type, List<List<BigDecimal>> coordinates) {}
}
