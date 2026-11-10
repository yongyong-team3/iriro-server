package com.iriro.server.trail.presentation;

import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

public record RouteListResponse(Long trailId, List<Item> routes) {
    public record Item(Long routeId, String name, List<String> activityTypes, BigDecimal distanceMeters,
                       Integer estimatedDurationSeconds, JsonNode geometry, List<StartPoint> startPoints) {}
    public record StartPoint(Long startPointId, String name, BigDecimal latitude, BigDecimal longitude) {}
}
