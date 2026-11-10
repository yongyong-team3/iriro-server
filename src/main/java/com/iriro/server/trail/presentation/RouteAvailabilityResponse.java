package com.iriro.server.trail.presentation;

import java.time.OffsetDateTime;
import java.util.List;

public record RouteAvailabilityResponse(Long routeId, Long startPointId, boolean startAllowed,
                                        String waterStatus, String dataSource, List<String> warnings,
                                        OffsetDateTime checkedAt) {
}
