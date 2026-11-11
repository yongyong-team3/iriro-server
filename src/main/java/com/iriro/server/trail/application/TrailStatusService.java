package com.iriro.server.trail.application;

import tools.jackson.databind.JsonNode;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.trail.domain.Trail;
import com.iriro.server.trail.infrastructure.AiStatusClient;
import com.iriro.server.trail.infrastructure.TrailRepository;
import com.iriro.server.trail.presentation.TrailStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class TrailStatusService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String WOLCHON_STATION_CODE = "1016607";
    private final TrailRepository trails;
    private final AiStatusClient aiStatusClient;
    private final boolean allowReplay;

    public TrailStatusService(TrailRepository trails, AiStatusClient aiStatusClient,
                              @Value("${iriro.ai.allow-replay:false}") boolean allowReplay) {
        this.trails = trails;
        this.aiStatusClient = aiStatusClient;
        this.allowReplay = allowReplay;
    }

    @Transactional(readOnly = true)
    public TrailStatusResponse get(Long trailId) {
        Trail trail = trails.findById(trailId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRAIL_NOT_FOUND", "하천길을 찾을 수 없습니다."));
        return status(trail);
    }

    public TrailStatusResponse status(Trail trail) {
        if (WOLCHON_STATION_CODE.equals(trail.getWaterStationCode())) {
            try {
                return liveStatus(trail.getId());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                return unavailable(trail.getId(), "월촌교", "수위 정보를 확인할 수 없습니다. 공식 통제 미확인");
            }
        }
        return unavailable(trail.getId(), null, "연결된 수위 관측소가 없습니다. 공식 통제 미확인");
    }

    private TrailStatusResponse unavailable(Long trailId, String stationName, String notice) {
        return new TrailStatusResponse(trailId, stationName, "UNAVAILABLE", "MISSING",
                null, null, "UNKNOWN", null, null, null, null, null, null, null,
                null, notice, OffsetDateTime.now(KOREA));
    }

    private TrailStatusResponse liveStatus(Long trailId) throws Exception {
        JsonNode body = aiStatusClient.get("/live/status");
        String source = text(body, "data_source");
        if ("replay".equals(source) && !allowReplay) {
            return unavailable(trailId, text(body.path("station"), "name"),
                    "시연 데이터는 현재 환경에서 제공하지 않습니다. 공식 통제 미확인");
        }
        if (!"replay".equals(source) && !"hrfco".equals(source)) {
            return unavailable(trailId, null, "수위 데이터 출처를 확인할 수 없습니다. 공식 통제 미확인");
        }
        String dataSource = "replay".equals(source) ? "REPLAY" : "LIVE";
        String stationName = text(body.path("station"), "name");
        if (!WOLCHON_STATION_CODE.equals(text(body.path("station"), "water_station_code"))) {
            return unavailable(trailId, stationName, "수위 관측소가 일치하지 않습니다. 공식 통제 미확인");
        }
        BigDecimal level = number(body, "water_level_m");
        OffsetDateTime observedAt = time(body, "observed_at");
        if (level == null && "LIVE".equals(dataSource)) {
            JsonNode points = aiStatusClient.get("/live/series?hours=24").path("points");
            for (int index = points.size() - 1; index >= 0; index--) {
                JsonNode point = points.get(index);
                BigDecimal candidate = number(point, "water_level_m");
                if (candidate != null) {
                    level = candidate;
                    observedAt = time(point, "time");
                    break;
                }
            }
        }
        OffsetDateTime generatedAt = OffsetDateTime.now(KOREA);
        boolean stale = "LIVE".equals(dataSource) && observedAt != null
                && Duration.between(observedAt, generatedAt).compareTo(Duration.ofMinutes(30)) > 0;
        if (stale) {
            return new TrailStatusResponse(trailId, stationName, dataSource, "STALE", null, null,
                    "UNKNOWN", null, observedAt, null, null, null, null, null, null,
                    "마지막 실측이 30분을 넘었습니다. 공식 통제 미확인·AI 참고 정보", generatedAt);
        }
        if (level == null) {
            return new TrailStatusResponse(trailId, stationName, dataSource, "MISSING", null, null,
                    "UNKNOWN", null, null, null, null, null, null, null, null,
                    "수위 정보를 확인할 수 없습니다. 공식 통제 미확인·AI 참고 정보", generatedAt);
        }
        JsonNode forecast = body.path("forecast_1h");
        JsonNode recovery = body.path("recovery");
        boolean hasForecast = forecast.isObject() && number(forecast, "level_m") != null;
        String waterStatus = level.compareTo(new BigDecimal("1.5")) >= 0 ? "HIGH_WATER" : "NORMAL";
        BigDecimal etaHours = number(recovery, "eta_hours_at_least");
        Integer etaMinutes = etaHours == null ? null : etaHours.multiply(new BigDecimal("60"))
                .setScale(0, java.math.RoundingMode.CEILING).intValue();
        return new TrailStatusResponse(trailId, stationName, dataSource, hasForecast ? "OK" : "PARTIAL",
                hasForecast ? text(forecast, "model_version") : null, text(recovery, "model_version"),
                waterStatus, level, observedAt, hasForecast ? number(forecast, "level_m") : null,
                hasForecast ? time(forecast, "target_time") : null,
                hasForecast ? upper(text(forecast, "trend")) : null,
                upper(text(recovery, "status")), etaMinutes, null,
                "공식 통제 미확인·AI 참고 정보", generatedAt);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private BigDecimal number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private OffsetDateTime time(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDateTime.parse(value).atZone(KOREA).toOffsetDateTime();
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
