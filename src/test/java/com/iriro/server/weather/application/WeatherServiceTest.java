package com.iriro.server.weather.application;

import com.iriro.server.weather.domain.GridCoordinateConverter;
import com.iriro.server.weather.domain.WeatherCondition;
import com.iriro.server.weather.infrastructure.KmaWeatherClient;
import com.iriro.server.weather.presentation.CurrentWeatherRequest;
import com.iriro.server.weather.presentation.CurrentWeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private KmaWeatherClient kmaWeatherClient;

    private GridCoordinateConverter gridCoordinateConverter;
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        gridCoordinateConverter = new GridCoordinateConverter();

        weatherService = new WeatherService(
                gridCoordinateConverter,
                kmaWeatherClient
        );
    }

    @Test
    @DisplayName("사용자 좌표로 현재 날씨를 정상적으로 조회한다")
    void getCurrentWeather_success() {

        // given
        double latitude = 37.5665;
        double longitude = 126.9780;

        CurrentWeatherRequest request =
                new CurrentWeatherRequest(latitude, longitude);

        Map<String, Integer> weatherData = new HashMap<>();
        weatherData.put("T1H", 15);
        weatherData.put("PTY", 0);
        weatherData.put("SKY", 1);

        GridCoordinateConverter.GridXY expectedGrid =
                gridCoordinateConverter.convertToGrid(
                        latitude,
                        longitude
                );

        when(kmaWeatherClient.fetchWeatherData(
                expectedGrid.getX(),
                expectedGrid.getY()
        )).thenReturn(weatherData);

        // when
        CurrentWeatherResponse response =
                weatherService.getCurrentWeather(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getAreaName())
                .isEqualTo("현재 위치 주변");

        assertThat(response.getTemperatureCelsius())
                .isEqualTo(15);

        assertThat(response.getCondition())
                .isEqualTo(WeatherCondition.SUNNY);

        assertThat(response.getObservedAt())
                .isNotNull();

        verify(kmaWeatherClient, times(1))
                .fetchWeatherData(
                        expectedGrid.getX(),
                        expectedGrid.getY()
                );
    }

    @Test
    @DisplayName("사용자 좌표가 없으면 기본 위치인 월촌교를 사용한다")
    void getCurrentWeather_defaultLocation() {

        // given
        CurrentWeatherRequest request =
                new CurrentWeatherRequest(null, null);

        Map<String, Integer> weatherData = new HashMap<>();
        weatherData.put("T1H", 20);
        weatherData.put("PTY", 0);
        weatherData.put("SKY", 3);

        GridCoordinateConverter.GridXY expectedGrid =
                gridCoordinateConverter.convertToGrid(
                        37.2794,
                        127.2274
                );

        when(kmaWeatherClient.fetchWeatherData(
                expectedGrid.getX(),
                expectedGrid.getY()
        )).thenReturn(weatherData);

        // when
        CurrentWeatherResponse response =
                weatherService.getCurrentWeather(request);

        // then
        assertThat(response.getAreaName())
                .isEqualTo("월촌교 주변");

        assertThat(response.getTemperatureCelsius())
                .isEqualTo(20);

        assertThat(response.getCondition())
                .isEqualTo(WeatherCondition.CLOUDY);

        verify(kmaWeatherClient, times(1))
                .fetchWeatherData(
                        expectedGrid.getX(),
                        expectedGrid.getY()
                );
    }

    @Test
    @DisplayName("기상청 API에 비가 오는 것으로 응답하면 RAINY를 반환한다")
    void getCurrentWeather_rainy() {

        // given
        CurrentWeatherRequest request =
                new CurrentWeatherRequest(37.5665, 126.9780);

        Map<String, Integer> weatherData = new HashMap<>();
        weatherData.put("T1H", 12);
        weatherData.put("PTY", 1);
        weatherData.put("SKY", 1);

        when(kmaWeatherClient.fetchWeatherData(anyInt(), anyInt()))
                .thenReturn(weatherData);

        // when
        CurrentWeatherResponse response =
                weatherService.getCurrentWeather(request);

        // then
        assertThat(response.getCondition())
                .isEqualTo(WeatherCondition.RAINY);

        assertThat(response.getTemperatureCelsius())
                .isEqualTo(12);
    }

    @Test
    @DisplayName("기상청 API 응답이 비어 있으면 UNKNOWN을 반환한다")
    void getCurrentWeather_emptyResponse() {

        // given
        CurrentWeatherRequest request =
                new CurrentWeatherRequest(37.5665, 126.9780);

        when(kmaWeatherClient.fetchWeatherData(anyInt(), anyInt()))
                .thenReturn(new HashMap<>());

        // when
        CurrentWeatherResponse response =
                weatherService.getCurrentWeather(request);

        // then
        assertThat(response.getTemperatureCelsius())
                .isNull();

        assertThat(response.getCondition())
                .isEqualTo(WeatherCondition.UNKNOWN);

        assertThat(response.getObservedAt())
                .isNull();

        assertThat(response.getResponseGeneratedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("강수형태가 눈이면 SNOWY를 반환한다")
    void getCurrentWeather_snowy() {

        // given
        CurrentWeatherRequest request =
                new CurrentWeatherRequest(37.5665, 126.9780);

        Map<String, Integer> weatherData = new HashMap<>();
        weatherData.put("T1H", 2);
        weatherData.put("PTY", 3);
        weatherData.put("SKY", 1);

        when(kmaWeatherClient.fetchWeatherData(anyInt(), anyInt()))
                .thenReturn(weatherData);

        // when
        CurrentWeatherResponse response =
                weatherService.getCurrentWeather(request);

        // then
        assertThat(response.getCondition())
                .isEqualTo(WeatherCondition.SNOWY);
    }

    @Test
    @DisplayName("위도만 전달해도 기본 위치를 사용한다")
    void getCurrentWeather_onlyLatitude() {

        // given
        CurrentWeatherRequest request =
                new CurrentWeatherRequest(37.5665, null);

        when(kmaWeatherClient.fetchWeatherData(anyInt(), anyInt()))
                .thenReturn(new HashMap<>());

        GridCoordinateConverter.GridXY expectedGrid =
                gridCoordinateConverter.convertToGrid(
                        37.2794,
                        127.2274
                );

        // when
        CurrentWeatherResponse response =
                weatherService.getCurrentWeather(request);

        // then
        assertThat(response.getAreaName())
                .isEqualTo("월촌교 주변");

        verify(kmaWeatherClient)
                .fetchWeatherData(
                        expectedGrid.getX(),
                        expectedGrid.getY()
                );
    }
}