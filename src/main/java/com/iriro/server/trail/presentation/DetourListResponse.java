package com.iriro.server.trail.presentation;

import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

public record DetourListResponse(Long routeId, List<Item> detours) {
    public record Item(Long detourId, String name, BigDecimal distanceMeters,
                       BigDecimal extraDistanceMeters, JsonNode geometry) {}
}
