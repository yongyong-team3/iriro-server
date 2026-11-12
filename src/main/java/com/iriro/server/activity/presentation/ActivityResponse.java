package com.iriro.server.activity.presentation;

import com.iriro.server.activity.domain.Activity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Builder
public class ActivityResponse {

    private Long activityId;

    private Long routeId;

    private String routeName;

    private String activityType;

    private OffsetDateTime startedAt;

    private BigDecimal distanceMeters;

    private Integer elapsedSeconds;

    private boolean simulated;

    public static ActivityResponse from(
            Activity activity,
            String routeName
    ) {

        return ActivityResponse.builder()
                .activityId(activity.getId())
                .routeId(activity.getRouteId())
                .routeName(routeName)
                .activityType(activity.getActivityType())

                // DB UTC -> API 한국 시간(+09:00)
                .startedAt(
                        activity.getStartedAt()
                                .atOffset(ZoneOffset.UTC)
                                .withOffsetSameInstant(
                                        ZoneOffset.ofHours(9)
                                )
                )

                .distanceMeters(activity.getDistanceMeters())
                .elapsedSeconds(activity.getElapsedSeconds())
                .simulated(activity.isSimulated())
                .build();
    }
}