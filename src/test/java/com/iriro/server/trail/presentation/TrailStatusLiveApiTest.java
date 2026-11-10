package com.iriro.server.trail.presentation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:trail-status-live-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class TrailStatusLiveApiTest {
    private static final HttpServer aiServer = createServer();
    private static final AtomicReference<String> mode = new AtomicReference<>("HIGH");
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void aiUrl(DynamicPropertyRegistry registry) {
        registry.add("integrations.ai.base-url", () -> "http://localhost:" + aiServer.getAddress().getPort());
    }

    @AfterAll
    static void stopAiServer() { aiServer.stop(0); }

    @BeforeEach
    void resetMode() { mode.set("HIGH"); }

    @Test
    void mapsLiveHighWaterForecastAndRecovery() throws Exception {
        Long trailId = insertLinkedTrail();
        mockMvc.perform(get("/api/v1/trails/{trailId}/status", trailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stationName").value("월촌교"))
                .andExpect(jsonPath("$.data.dataSource").value("LIVE"))
                .andExpect(jsonPath("$.data.dataStatus").value("OK"))
                .andExpect(jsonPath("$.data.waterStatus").value("HIGH_WATER"))
                .andExpect(jsonPath("$.data.currentWaterLevelMeters").value(1.85))
                .andExpect(jsonPath("$.data.predictedWaterLevelMeters").value(1.75))
                .andExpect(jsonPath("$.data.trend").value("FALLING"))
                .andExpect(jsonPath("$.data.recoveryStatus").value("PREDICTED"))
                .andExpect(jsonPath("$.data.recoveryEtaAtLeastMinutes").value(240));

        mockMvc.perform(get("/api/v1/trails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trails[0].waterStatus").value("HIGH_WATER"))
                .andExpect(jsonPath("$.data.trails[0].dataSource").value("LIVE"));
    }

    @Test
    void liveObservationWithoutForecastIsPartial() throws Exception {
        mode.set("PARTIAL");
        Long trailId = insertLinkedTrail();
        mockMvc.perform(get("/api/v1/trails/{trailId}/status", trailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data.waterStatus").value("NORMAL"))
                .andExpect(jsonPath("$.data.predictedWaterLevelMeters").value((String) null));
    }

    @Test
    void oldLastObservationIsStaleAndHidden() throws Exception {
        mode.set("STALE");
        Long trailId = insertLinkedTrail();
        mockMvc.perform(get("/api/v1/trails/{trailId}/status", trailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("STALE"))
                .andExpect(jsonPath("$.data.waterStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.currentWaterLevelMeters").value((String) null))
                .andExpect(jsonPath("$.data.observedAt").isNotEmpty());
    }

    @Test
    void replayIsMarkedAndNotComparedWithWallClock() throws Exception {
        mode.set("REPLAY");
        Long trailId = insertLinkedTrail();
        mockMvc.perform(get("/api/v1/trails/{trailId}/status", trailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataSource").value("REPLAY"))
                .andExpect(jsonPath("$.data.dataStatus").value("OK"))
                .andExpect(jsonPath("$.data.officialControlStatus").value((String) null));
    }

    @Test
    void missingObservationHasNullObservedAt() throws Exception {
        mode.set("MISSING");
        Long trailId = insertLinkedTrail();
        mockMvc.perform(get("/api/v1/trails/{trailId}/status", trailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("MISSING"))
                .andExpect(jsonPath("$.data.observedAt").value((String) null));
    }

    @Test
    void aiFailureReturnsUnknownWithoutServerError() throws Exception {
        mode.set("FAILURE");
        Long trailId = insertLinkedTrail();
        mockMvc.perform(get("/api/v1/trails/{trailId}/status", trailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataSource").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.waterStatus").value("UNKNOWN"));
    }

    @Test
    void highWaterStillAllowsRouteStartWithWarning() throws Exception {
        Long trailId = insertLinkedTrail();
        jdbcTemplate.update("""
                INSERT INTO routes (trail_id, name, distance_meters, estimated_duration_seconds,
                    geometry_geojson, created_at, updated_at)
                VALUES (?, '경안천 코스', 2400, 1800,
                    '{"type":"LineString","coordinates":[[127.2355,37.3245],[127.2360,37.3250]]}',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, trailId);
        Long routeId = jdbcTemplate.queryForObject("SELECT id FROM routes WHERE trail_id = ?", Long.class, trailId);
        jdbcTemplate.update("INSERT INTO route_start_points (route_id, name, latitude, longitude) VALUES (?, '월촌교', 37.324500, 127.235500)", routeId);
        Long startId = jdbcTemplate.queryForObject("SELECT id FROM route_start_points WHERE route_id = ?", Long.class, routeId);

        mockMvc.perform(get("/api/v1/routes/{routeId}/availability", routeId)
                        .param("startPointId", startId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startAllowed").value(true))
                .andExpect(jsonPath("$.data.waterStatus").value("HIGH_WATER"))
                .andExpect(jsonPath("$.data.warnings[0]").value("수위가 높습니다. 이용 전 현장을 확인해 주세요."));
    }

    private Long insertLinkedTrail() {
        jdbcTemplate.update("""
                INSERT INTO trails (name, center_latitude, center_longitude, length_meters,
                    geometry_geojson, water_station_code, created_at, updated_at)
                VALUES ('경안천', 37.324500, 127.235500, 2400,
                    '{"type":"LineString","coordinates":[[127.2355,37.3245],[127.2360,37.3250]]}',
                    '1016607', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        return jdbcTemplate.queryForObject("SELECT id FROM trails WHERE name = '경안천'", Long.class);
    }

    private static HttpServer createServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/live/status", exchange -> {
                if ("FAILURE".equals(mode.get())) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
                String now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String next = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String response = """
                        {"station":{"name":"월촌교","water_station_code":"1016607"},"data_source":"hrfco",
                         "observed_at":"%s","water_level_m":1.85,"data_status":"ok",
                         "forecast_1h":{"target_time":"%s","level_m":1.75,"trend":"falling","model_version":"test-model"},
                         "recovery":{"status":"predicted","eta_hours_at_least":4.0,"model_version":"recovery-v0.5"}}
                        """.formatted(now, next);
                if ("PARTIAL".equals(mode.get())) {
                    response = """
                            {"station":{"name":"월촌교","water_station_code":"1016607"},"data_source":"hrfco",
                             "observed_at":"%s","water_level_m":1.2,"data_status":"partial",
                             "forecast_1h":null,"recovery":{"status":"below_threshold","model_version":"recovery-v0.5"}}
                            """.formatted(now);
                } else if ("STALE".equals(mode.get())) {
                    response = """
                            {"station":{"name":"월촌교","water_station_code":"1016607"},"data_source":"hrfco",
                             "observed_at":"%s","water_level_m":null,"data_status":"missing",
                             "forecast_1h":null,"recovery":{"status":"insufficient_data"}}
                            """.formatted(now);
                } else if ("MISSING".equals(mode.get())) {
                    response = """
                            {"station":{"name":"월촌교","water_station_code":"1016607"},"data_source":"hrfco",
                             "observed_at":"%s","water_level_m":null,"data_status":"missing",
                             "forecast_1h":null,"recovery":{"status":"insufficient_data"}}
                            """.formatted(now);
                } else if ("REPLAY".equals(mode.get())) {
                    response = response.replace("\"data_source\":\"hrfco\"", "\"data_source\":\"replay\"")
                            .replace("\"observed_at\":\"" + now + "\"", "\"observed_at\":\"2026-07-10T01:00\"");
                }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.createContext("/live/series", exchange -> {
                String old = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(31)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String response = "MISSING".equals(mode.get()) ? "{\"points\":[]}"
                        : "{\"points\":[{\"time\":\"" + old + "\",\"water_level_m\":1.8}]}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
