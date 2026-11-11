package com.iriro.server.activity.presentation;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ActivityMonthlyStatsResponse {

    private String month;

    private long activityCount;

    private BigDecimal distanceMeters;

    private long elapsedSeconds;

    private List<LocalDate> activityDates;

    private long currentStreakDays;

    private long longestStreakDays;
}