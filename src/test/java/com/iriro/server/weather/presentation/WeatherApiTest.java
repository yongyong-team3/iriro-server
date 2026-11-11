package com.iriro.server.weather.presentation;

import com.iriro.server.weather.infrastructure.KmaWeatherClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:weather-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
class WeatherApiTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean KmaWeatherClient kmaWeatherClient;

    @Test
    void rejectsIncompleteAndOutOfRangeCoordinates() throws Exception {
        mockMvc.perform(get("/api/v1/weather/current").param("latitude", "37.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/weather/current").param("longitude", "127.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/weather/current")
                        .param("latitude", "91").param("longitude", "127.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void defaultsToWolchonWhenBothCoordinatesAreAbsent() throws Exception {
        mockMvc.perform(get("/api/v1/weather/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.areaName").value("월촌교 주변"))
                .andExpect(jsonPath("$.data.condition").value("UNKNOWN"));
    }
}
