package com.iriro.server.weather.application;

import com.iriro.server.weather.presentation.CurrentWeatherRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "WEATHER_API_KEY", matches = ".+")
class WeatherLiveIntegrationTest {
    @Autowired WeatherService service;

    @Test
    void realKmaResponseIsConvertedToWeatherContract() {
        var response = service.getCurrentWeather(new CurrentWeatherRequest());
        assertThat(response.getTemperatureCelsius()).isNotNull();
        assertThat(response.getObservedAt()).isNotNull();
        assertThat(response.getCondition()).isNotNull();
    }
}
