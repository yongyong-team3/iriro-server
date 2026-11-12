package com.iriro.server.activity.presentation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record WeeklyActivityStatsResponse(LocalDate weekStartDate, LocalDate weekEndDate,
                                          long activityCount, BigDecimal distanceMeters, long elapsedSeconds,
                                          List<Daily> daily, List<Recent> recentActivities) {
    public record Daily(LocalDate date, long activityCount, BigDecimal distanceMeters, long elapsedSeconds) {}
    public record Recent(Long activityId, String routeName, String activityType,
                         BigDecimal distanceMeters, OffsetDateTime startedAt) {}
}
