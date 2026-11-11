package com.iriro.server.activity.presentation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ActivityListResponse(List<Item> content, int page, int size, long totalElements,
                                   int totalPages, boolean hasNext) {
    public record Item(Long activityId, Long routeId, String routeName, String activityType,
                       OffsetDateTime startedAt, BigDecimal distanceMeters, int elapsedSeconds,
                       boolean simulated) {}
}
