package com.iriro.server.activity.presentation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MonthlyActivityStatsResponse(String month, long activityCount, BigDecimal distanceMeters,
                                           long elapsedSeconds, List<LocalDate> activityDates,
                                           long currentStreakDays, long longestStreakDays) {
}
