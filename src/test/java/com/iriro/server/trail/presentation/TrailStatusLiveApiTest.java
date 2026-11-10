package com.iriro.server.trail.presentation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void aiUrl(DynamicPropertyRegistry registry) {
        registry.add("integrations.ai.base-url", () -> "http://localhost:" + aiServer.getAddress().getPort());
    }

    @AfterAll
    static void stopAiServer() { aiServer.stop(0); }

    @Test
    void mapsLiveHighWaterForecastAndRecovery() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO trails (name, center_latitude, center_longitude, length_meters,
                    geometry_geojson, water_station_code, created_at, updated_at)
                VALUES ('경안천', 37.324500, 127.235500, 2400,
                    '{"type":"LineString","coordinates":[[127.2355,37.3245],[127.2360,37.3250]]}',
                    '1016607', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long trailId = jdbcTemplate.queryForObject("SELECT id FROM trails WHERE name = '경안천'", Long.class);
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
    }

    private static HttpServer createServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/live/status", exchange -> {
                String now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String next = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String response = """
                        {"station":{"name":"월촌교","water_station_code":"1016607"},"data_source":"hrfco",
                         "observed_at":"%s","water_level_m":1.85,"data_status":"ok",
                         "forecast_1h":{"target_time":"%s","level_m":1.75,"trend":"falling","model_version":"test-model"},
                         "recovery":{"status":"predicted","eta_hours_at_least":4.0,"model_version":"recovery-v0.5"}}
                        """.formatted(now, next);
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
