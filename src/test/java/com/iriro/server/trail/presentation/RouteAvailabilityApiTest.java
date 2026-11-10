package com.iriro.server.trail.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:route-availability-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class RouteAvailabilityApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void unknownWaterStillAllowsStartWithWarning() throws Exception {
        Long routeId = route();
        jdbcTemplate.update("INSERT INTO route_start_points (route_id, name, latitude, longitude) VALUES (?, '월촌교', 37.338470, 127.237730)", routeId);
        Long startPointId = jdbcTemplate.queryForObject("SELECT id FROM route_start_points WHERE route_id = ?", Long.class, routeId);
        mockMvc.perform(get("/api/v1/routes/{routeId}/availability", routeId)
                        .param("startPointId", startPointId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startAllowed").value(true))
                .andExpect(jsonPath("$.data.waterStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.dataSource").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.warnings").isNotEmpty())
                .andExpect(jsonPath("$.data.checkedAt").isNotEmpty());
    }

    @Test
    void rejectsStartPointOutsideRoute() throws Exception {
        Long routeId = route();
        mockMvc.perform(get("/api/v1/routes/{routeId}/availability", routeId)
                        .param("startPointId", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_START_POINT"));
        mockMvc.perform(get("/api/v1/routes/{routeId}/availability", routeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_START_POINT"));
    }

    @Test
    void missingRouteReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/routes/999999/availability").param("startPointId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }

    private Long route() {
        jdbcTemplate.update("""
                INSERT INTO trails (name, center_latitude, center_longitude, length_meters,
                    geometry_geojson, created_at, updated_at)
                VALUES ('능원천', 37.338470, 127.237730, 1364.50,
                    '{"type":"LineString","coordinates":[[127.23773,37.33847],[127.22542,37.34358]]}',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long trailId = jdbcTemplate.queryForObject("SELECT id FROM trails WHERE name = '능원천'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO routes (trail_id, name, distance_meters, estimated_duration_seconds,
                    geometry_geojson, created_at, updated_at)
                VALUES (?, '월촌교→매산교', 1364.50, 1200,
                    '{"type":"LineString","coordinates":[[127.23773,37.33847],[127.22542,37.34358]]}',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, trailId);
        return jdbcTemplate.queryForObject("SELECT id FROM routes WHERE trail_id = ?", Long.class, trailId);
    }
}
