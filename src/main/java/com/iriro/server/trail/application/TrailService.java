package com.iriro.server.trail.application;

import tools.jackson.databind.ObjectMapper;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.trail.domain.Trail;
import com.iriro.server.trail.infrastructure.TrailRepository;
import com.iriro.server.trail.presentation.TrailListResponse;
import com.iriro.server.trail.presentation.TrailDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class TrailService {
    private final TrailRepository trails;
    private final ObjectMapper objectMapper;
    private final TrailStatusService statusService;

    public TrailService(TrailRepository trails, ObjectMapper objectMapper, TrailStatusService statusService) {
        this.trails = trails;
        this.objectMapper = objectMapper;
        this.statusService = statusService;
    }

    @Transactional(readOnly = true)
    public TrailDetailResponse detail(Long trailId) {
        Trail trail = trails.findById(trailId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRAIL_NOT_FOUND", "하천길을 찾을 수 없습니다."));
        try {
            return new TrailDetailResponse(trail.getId(), trail.getName(), trail.getSectionName(),
                    trail.getDescription(), trail.getLengthMeters(), trail.getCenterLatitude(),
                    trail.getCenterLongitude(), objectMapper.readTree(trail.getGeometryGeojson()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored trail geometry is invalid", exception);
        }
    }

    @Transactional(readOnly = true)
    public TrailListResponse list(Double latitude, Double longitude, String query) {
        if ((latitude == null) != (longitude == null)
                || (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90))
                || (longitude != null && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
        }
        String keyword = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        var items = trails.findAll().stream()
                .filter(trail -> keyword.isEmpty() || trail.getName().toLowerCase(java.util.Locale.ROOT).contains(keyword)
                        || (trail.getSectionName() != null
                        && trail.getSectionName().toLowerCase(java.util.Locale.ROOT).contains(keyword)))
                .map(trail -> item(trail, latitude, longitude))
                .toList();
        return new TrailListResponse(items, OffsetDateTime.now());
    }

    private TrailListResponse.Item item(Trail trail, Double latitude, Double longitude) {
        var status = statusService.status(trail);
        Long distance = latitude == null ? null : Math.round(distanceMeters(latitude, longitude,
                trail.getCenterLatitude().doubleValue(), trail.getCenterLongitude().doubleValue()));
        return new TrailListResponse.Item(trail.getId(), trail.getName(), trail.getSectionName(),
                trail.getCenterLatitude(), trail.getCenterLongitude(), distance,
                trail.getLengthMeters(), status.waterStatus(), status.dataSource());
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * 6_371_000 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
