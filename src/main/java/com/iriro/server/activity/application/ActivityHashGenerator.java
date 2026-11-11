package com.iriro.server.activity.application;

import tools.jackson.databind.ObjectMapper;
import com.iriro.server.activity.presentation.ActivityCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ActivityHashGenerator {

    private final ObjectMapper objectMapper;

    public String generate(
            ActivityCreateRequest request,
            boolean simulated
    ) {

        try {
            Map<String, Object> normalized = new LinkedHashMap<>();

            normalized.put("routeId", request.getRouteId());
            normalized.put("startPointId", request.getStartPointId());
            normalized.put("activityType", request.getActivityType());
            normalized.put("startedAt", request.getStartedAt());
            normalized.put("endedAt", request.getEndedAt());
            normalized.put("elapsedSeconds", request.getElapsedSeconds());
            normalized.put("distanceMeters", request.getDistanceMeters());
            normalized.put("trackPoints", request.getTrackPoints());
            normalized.put(
                    "saveAsFavorite",
                    Boolean.TRUE.equals(request.getSaveAsFavorite())
            );
            normalized.put("simulated", simulated);

            byte[] json = objectMapper.writeValueAsBytes(normalized);

            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(json);

            return HexFormat.of().formatHex(hash);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "요청 해시 생성에 실패했습니다.", e
            );
        }
    }
}