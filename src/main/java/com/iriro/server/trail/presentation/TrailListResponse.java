package com.iriro.server.trail.presentation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TrailListResponse(List<Item> trails, OffsetDateTime responseGeneratedAt) {
    public record Item(Long trailId, String name, String sectionName, BigDecimal latitude,
                       BigDecimal longitude, Long distanceMeters, BigDecimal lengthMeters,
                       String waterStatus, String dataSource) {}
}
