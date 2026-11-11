package com.iriro.server.activity.presentation;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class ActivityWeeklyStatsResponse {

    private LocalDate weekStartDate;

    private LocalDate weekEndDate;

    private long activityCount;

    private BigDecimal distanceMeters;

    private long elapsedSeconds;

    private List<Daily> daily;

    private List<RecentActivity> recentActivities;

    /**
     * 일별 통계
     */
    @Getter
    @Builder
    public static class Daily {

        private LocalDate date;

        private long activityCount;

        private BigDecimal distanceMeters;

        private long elapsedSeconds;
    }

    /**
     * 최근 활동
     */
    @Getter
    @Builder
    public static class RecentActivity {

        private Long activityId;

        private String routeName;

        private String activityType;

        private BigDecimal distanceMeters;

        private OffsetDateTime startedAt;
    }
}