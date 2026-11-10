package com.iriro.server.weather.application;

import com.iriro.server.weather.domain.GridCoordinateConverter;
import com.iriro.server.weather.domain.WeatherCondition;
import com.iriro.server.weather.infrastructure.KmaWeatherClient;
import com.iriro.server.weather.presentation.CurrentWeatherRequest;
import com.iriro.server.weather.presentation.CurrentWeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final double DEFAULT_LATITUDE = 37.2794;
    private static final double DEFAULT_LONGITUDE = 127.2274;
    private static final String DEFAULT_AREA_NAME = "월촌교 주변";

    private final GridCoordinateConverter gridCoordinateConverter;
    private final KmaWeatherClient kmaWeatherClient;

    public CurrentWeatherResponse getCurrentWeather(
            CurrentWeatherRequest request
    ) {

        // 1. 위치 결정
        boolean isDefaultLocation =
                request.getLatitude() == null
                        || request.getLongitude() == null;

        double targetLat = isDefaultLocation
                ? DEFAULT_LATITUDE
                : request.getLatitude();

        double targetLon = isDefaultLocation
                ? DEFAULT_LONGITUDE
                : request.getLongitude();

        String areaName = isDefaultLocation
                ? DEFAULT_AREA_NAME
                : "현재 위치 주변";

        // 2. 위도, 경도 -> 기상청 격자 좌표 변환
        GridCoordinateConverter.GridXY gridXY =
                gridCoordinateConverter.convertToGrid(
                        targetLat,
                        targetLon
                );

        // 3. 기상청 API 호출
        Map<String, Integer> weatherData =
                kmaWeatherClient.fetchWeatherData(
                        gridXY.getX(),
                        gridXY.getY()
                );

        // 4. 응답 데이터 추출
        Integer temperatureCelsius = weatherData.get("T1H");
        Integer ptyCode = weatherData.get("PTY");
        Integer skyCode = weatherData.get("SKY");

        // 5. 날씨 상태 변환
        WeatherCondition condition =
                WeatherCondition.of(ptyCode, skyCode);

        // 6. 응답 DTO 생성
        return CurrentWeatherResponse.of(
                areaName,
                temperatureCelsius,
                condition,
                temperatureCelsius != null
                        ? OffsetDateTime.now(ZoneId.of("Asia/Seoul"))
                        : null
        );
    }
}
