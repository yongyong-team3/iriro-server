package com.iriro.server.trail.application;

import com.iriro.server.trail.domain.Trail;
import com.iriro.server.trail.infrastructure.AiStatusClient;
import com.iriro.server.trail.infrastructure.TrailRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrailStatusReplayIsolationTest {
    @Test
    void hidesReplayObservationsWhenReplayIsNotAllowed() throws Exception {
        TrailRepository trails = mock(TrailRepository.class);
        AiStatusClient ai = mock(AiStatusClient.class);
        Trail trail = mock(Trail.class);
        when(trail.getId()).thenReturn(1L);
        when(trail.getWaterStationCode()).thenReturn("1016607");
        when(ai.get("/live/status")).thenReturn(new ObjectMapper().readTree("""
                {"data_source":"replay","station":{"name":"월촌교","water_station_code":"1016607"},
                 "observed_at":"2026-07-16T11:40","water_level_m":1.85,
                 "forecast_1h":{"level_m":1.4},"recovery":{"status":"predicted","eta_hours_at_least":4}}
                """));

        var result = new TrailStatusService(trails, ai, false).status(trail);

        assertThat(result.dataSource()).isEqualTo("UNAVAILABLE");
        assertThat(result.currentWaterLevelMeters()).isNull();
        assertThat(result.predictedWaterLevelMeters()).isNull();
    }
}
