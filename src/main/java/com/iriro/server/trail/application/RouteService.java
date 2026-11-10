package com.iriro.server.trail.application;

import com.iriro.server.common.application.ApiException;
import com.iriro.server.trail.domain.Route;
import com.iriro.server.trail.infrastructure.RouteRepository;
import com.iriro.server.trail.infrastructure.RouteStartPointRepository;
import com.iriro.server.trail.infrastructure.TrailRepository;
import com.iriro.server.trail.presentation.RouteListResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

@Service
public class RouteService {
    private static final Set<String> ACTIVITY_TYPES = Set.of("WALK", "RUN", "BIKE");

    private final TrailRepository trails;
    private final RouteRepository routes;
    private final RouteStartPointRepository startPoints;
    private final ObjectMapper objectMapper;

    public RouteService(TrailRepository trails, RouteRepository routes,
                        RouteStartPointRepository startPoints, ObjectMapper objectMapper) {
        this.trails = trails;
        this.routes = routes;
        this.startPoints = startPoints;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RouteListResponse list(Long trailId, String activityType) {
        if (activityType != null && !ACTIVITY_TYPES.contains(activityType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
        }
        if (!trails.existsById(trailId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRAIL_NOT_FOUND", "하천길을 찾을 수 없습니다.");
        }
        List<RouteListResponse.Item> items = routes.findByTrailIdOrderByIdAsc(trailId).stream()
                .filter(route -> activityType == null || route.getActivityTypes().contains(activityType))
                .map(this::item)
                .toList();
        return new RouteListResponse(trailId, items);
    }

    private RouteListResponse.Item item(Route route) {
        var points = startPoints.findByRouteIdOrderByIdAsc(route.getId()).stream()
                .map(point -> new RouteListResponse.StartPoint(point.getId(), point.getName(),
                        point.getLatitude(), point.getLongitude()))
                .toList();
        return new RouteListResponse.Item(route.getId(), route.getName(),
                route.getActivityTypes().stream().sorted().toList(), route.getDistanceMeters(),
                route.getEstimatedDurationSeconds(), objectMapper.readTree(route.getGeometryGeojson()), points);
    }
}
