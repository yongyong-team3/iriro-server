package com.iriro.server.trail.presentation;

import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

public record RouteDetailResponse(Long routeId, Long trailId, String name, List<String> activityTypes,
                                  BigDecimal distanceMeters, Integer estimatedDurationSeconds,
                                  JsonNode geometry, List<RouteListResponse.StartPoint> startPoints,
                                  String waterStatus) {
}
