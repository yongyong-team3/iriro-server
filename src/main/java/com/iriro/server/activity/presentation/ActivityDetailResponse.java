package com.iriro.server.activity.presentation;

import com.iriro.server.activity.domain.Activity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
public class ActivityDetailResponse {

    private Long activityId;

    private Long routeId;

    private String routeName;

    private String activityType;

    private OffsetDateTime startedAt;

    private OffsetDateTime endedAt;

    private BigDecimal distanceMeters;

    private Integer elapsedSeconds;

    private boolean simulated;

    private Long averagePaceSecondsPerKm;

    private Geometry geometry;

    /**
     * GeoJSON Geometry
     */
    @Getter
    @Builder
    public static class Geometry {

        private String type;

        private java.util.List<java.util.List<Double>> coordinates;
    }

    public static ActivityDetailResponse from(
            Activity activity,
            String routeName,
            Geometry geometry
    ) {

        // 평균 페이스 계산 (초/km)
        long averagePace = Math.round(
                activity.getElapsedSeconds()
                        / (activity.getDistanceMeters().doubleValue() / 1000.0)
        );

        return ActivityDetailResponse.builder()
                .activityId(activity.getId())
                .routeId(activity.getRouteId())
                .routeName(routeName)
                .activityType(activity.getActivityType())

                // UTC -> 한국 시간
                .startedAt(
                        activity.getStartedAt()
                                .atOffset(ZoneOffset.UTC)
                                .withOffsetSameInstant(ZoneOffset.ofHours(9))
                )
                .endedAt(
                        activity.getEndedAt()
                                .atOffset(ZoneOffset.UTC)
                                .withOffsetSameInstant(ZoneOffset.ofHours(9))
                )

                .distanceMeters(activity.getDistanceMeters())
                .elapsedSeconds(activity.getElapsedSeconds())
                .simulated(activity.isSimulated())
                .averagePaceSecondsPerKm(averagePace)
                .geometry(geometry)
                .build();
    }
}