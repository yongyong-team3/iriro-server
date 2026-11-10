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
        "spring.datasource.url=jdbc:h2:mem:route-list-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class RouteListApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void existingTrailWithoutRoutesReturnsEmptyList() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO trails (name, section_name, center_latitude, center_longitude,
                    length_meters, geometry_geojson, created_at, updated_at)
                VALUES ('능원천 하천길', '월촌교~매산교 구간', 37.338470, 127.237730,
                    1364.50, '{"type":"LineString","coordinates":[[127.23773,37.33847],[127.22542,37.34358]]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long trailId = jdbcTemplate.queryForObject("SELECT id FROM trails WHERE name = '능원천 하천길'", Long.class);
        mockMvc.perform(get("/api/v1/trails/{trailId}/routes", trailId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trailId").value(trailId))
                .andExpect(jsonPath("$.data.routes").isEmpty());
    }

    @Test
    void rejectsUnknownActivityType() throws Exception {
        mockMvc.perform(get("/api/v1/trails/1/routes").param("activityType", "SWIM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
