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
        "spring.datasource.url=jdbc:h2:mem:detour-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class DetourApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void returnsEmptyArrayOrRegisteredDetour() throws Exception {
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
        Long routeId = jdbcTemplate.queryForObject("SELECT id FROM routes WHERE trail_id = ?", Long.class, trailId);
        mockMvc.perform(get("/api/v1/routes/{routeId}/detours", routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routeId").value(routeId))
                .andExpect(jsonPath("$.data.detours").isEmpty());

        jdbcTemplate.update("""
                INSERT INTO detours (route_id, name, distance_meters, extra_distance_meters, geometry_geojson)
                VALUES (?, '문현로 우회 코스', 1281.00, 0.00,
                    '{"type":"LineString","coordinates":[[127.23773,37.33847],[127.22542,37.34358]]}')
                """, routeId);
        mockMvc.perform(get("/api/v1/routes/{routeId}/detours", routeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detours[0].name").value("문현로 우회 코스"))
                .andExpect(jsonPath("$.data.detours[0].distanceMeters").value(1281.0))
                .andExpect(jsonPath("$.data.detours[0].extraDistanceMeters").value(0.0))
                .andExpect(jsonPath("$.data.detours[0].geometry.type").value("LineString"));
    }

    @Test
    void missingRouteReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/routes/999999/detours"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }
}
