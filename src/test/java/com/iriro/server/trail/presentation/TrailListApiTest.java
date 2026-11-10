package com.iriro.server.trail.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:trail-list-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TrailListApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void returnsRegisteredTrailAndDistanceFromRequestedLocation() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO trails (name, section_name, description, center_latitude, center_longitude,
                    length_meters, geometry_geojson, created_at, updated_at)
                VALUES ('능원천 하천길', '월촌교~매산교 구간', '등록 구간', 37.338470, 127.237730,
                    1364.50, '{"type":"LineString","coordinates":[[127.23773,37.33847],[127.22542,37.34358]]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        mockMvc.perform(get("/api/v1/trails")
                        .param("latitude", "37.33847")
                        .param("longitude", "127.23773"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trails.length()").value(1))
                .andExpect(jsonPath("$.data.trails[0].name").value("능원천 하천길"))
                .andExpect(jsonPath("$.data.trails[0].sectionName").value("월촌교~매산교 구간"))
                .andExpect(jsonPath("$.data.trails[0].distanceMeters").value(0))
                .andExpect(jsonPath("$.data.trails[0].waterStatus").value("UNKNOWN"));

        mockMvc.perform(get("/api/v1/trails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trails[0].distanceMeters").value((String) null));
    }

    @Test
    @Transactional
    void searchesBothTrailNameAndSectionName() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO trails (name, section_name, center_latitude, center_longitude,
                    length_meters, geometry_geojson, created_at, updated_at)
                VALUES ('능원천 하천길', '월촌교~매산교 구간', 37.338470, 127.237730,
                    1364.50, '{"type":"LineString","coordinates":[[127.23773,37.33847],[127.22542,37.34358]]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        mockMvc.perform(get("/api/v1/trails").param("query", "능원천"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trails.length()").value(1));
        mockMvc.perform(get("/api/v1/trails").param("query", "매산교"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trails.length()").value(1));
        mockMvc.perform(get("/api/v1/trails").param("query", "없는구간"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trails").isEmpty());
    }

    @Test
    void listsTrailsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/trails"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.trails").isArray())
                .andExpect(jsonPath("$.data.responseGeneratedAt").isNotEmpty());
    }

    @Test
    void rejectsInvalidLocation() throws Exception {
        mockMvc.perform(get("/api/v1/trails")
                        .param("latitude", "91")
                        .param("longitude", "127.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/trails").param("latitude", "37.33847"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
