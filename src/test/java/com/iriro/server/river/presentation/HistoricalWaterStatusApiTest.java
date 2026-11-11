package com.iriro.server.river.presentation;

import com.iriro.server.trail.infrastructure.AiStatusClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:historical-water-status;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class HistoricalWaterStatusApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiStatusClient aiStatusClient;

    @Test
    void returnsOnlyStoredObservationAndContemporaneousAiDecision() throws Exception {
        snapshot("2024-07-18 03:00:00", "2024-07-18 03:01:00");

        mockMvc.perform(get("/api/v1/water-levels/history")
                        .param("at", "2024-07-18T12:05:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedAt").value("2024-07-18T12:05:00+09:00"))
                .andExpect(jsonPath("$.data.observedAt").value("2024-07-18T12:00:00+09:00"))
                .andExpect(jsonPath("$.data.currentWaterLevelMeters").value(1.23))
                .andExpect(jsonPath("$.data.recoveryStatus").value("PREDICTED"))
                .andExpect(jsonPath("$.data.forecastModelVersion").value("test-v1"));
        verifyNoInteractions(aiStatusClient);
    }

    @Test
    void missingOrTooOldSnapshotIsNotFoundWithoutReplayFallback() throws Exception {
        snapshot("2024-07-18 03:00:00", "2024-07-18 03:01:00");
        mockMvc.perform(get("/api/v1/water-levels/history")
                        .param("at", "2024-07-18T12:11:00+09:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HISTORICAL_SNAPSHOT_NOT_FOUND"));
        verifyNoInteractions(aiStatusClient);
    }

    @Test
    void decisionCreatedAfterRequestedTimeIsNotHistoricalEvidence() throws Exception {
        snapshot("2024-07-18 03:00:00", "2024-07-18 03:06:00");
        mockMvc.perform(get("/api/v1/water-levels/history")
                        .param("at", "2024-07-18T12:05:00+09:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HISTORICAL_SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void snapshotStoredAfterRequestedTimeIsNotHistoricalEvidence() throws Exception {
        snapshot("2024-07-18 03:00:00", "2024-07-18 03:01:00", "2024-07-18 03:06:00");
        mockMvc.perform(get("/api/v1/water-levels/history")
                        .param("at", "2024-07-18T12:05:00+09:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HISTORICAL_SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void replaySnapshotCannotMasqueradeAsHistoricalLiveDecision() throws Exception {
        snapshot("2024-07-18 03:00:00", "2024-07-18 03:01:00");
        jdbc.update("UPDATE historical_status_snapshots SET data_source = 'REPLAY'");
        mockMvc.perform(get("/api/v1/water-levels/history")
                        .param("at", "2024-07-18T12:05:00+09:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HISTORICAL_SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void swaggerDocumentsHistoricalQueryAndErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/water-levels/history'].get.parameters[0].name").value("at"))
                .andExpect(jsonPath("$.paths['/api/v1/water-levels/history'].get.responses['404'].description")
                        .value("HISTORICAL_SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidAndFutureRequests() throws Exception {
        mockMvc.perform(get("/api/v1/water-levels/history"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/water-levels/history").param("at", "2024-07-18"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/water-levels/history")
                        .param("at", "2999-07-18T12:00:00+09:00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/water-levels/history")
                        .param("at", "2024-07-18T12:00:00+09:00").param("stationCode", "unknown"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void snapshot(String observedAt, String evaluatedAt) {
        snapshot(observedAt, evaluatedAt, evaluatedAt);
    }

    private void snapshot(String observedAt, String evaluatedAt, String recordedAt) {
        jdbc.update("""
                INSERT INTO historical_status_snapshots
                    (station_code, observed_at, ai_evaluated_at, snapshot_recorded_at, water_level_m,
                     water_status, predicted_water_level_m, forecast_target_at, trend, recovery_status,
                     recovery_eta_at_least_minutes, forecast_model_version, recovery_model_version,
                     data_source, data_status, official_control_status)
                VALUES ('1016607', ?, ?, ?, 1.23, 'NORMAL', 1.11, '2024-07-18 04:00:00',
                        'FALLING', 'PREDICTED', 60, 'test-v1', 'recovery-v1', 'LIVE', 'OK', NULL)
                """, observedAt, evaluatedAt, recordedAt);
    }
}
