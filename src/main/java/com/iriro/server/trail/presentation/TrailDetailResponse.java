package com.iriro.server.trail.presentation;

import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record TrailDetailResponse(Long trailId, String name, String sectionName, String description,
                                  BigDecimal lengthMeters, BigDecimal latitude, BigDecimal longitude,
                                  JsonNode geometry) {
}
