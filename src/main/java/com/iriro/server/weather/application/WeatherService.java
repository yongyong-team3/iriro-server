package com.iriro.server.weather.application;

import com.iriro.server.common.application.ApiException;
import com.iriro.server.weather.domain.GridCoordinateConverter;
import com.iriro.server.weather.domain.WeatherCondition;
import com.iriro.server.weather.infrastructure.KmaWeatherClient;
import com.iriro.server.weather.presentation.CurrentWeatherRequest;
import com.iriro.server.weather.presentation.CurrentWeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final double DEFAULT_LATITUDE = 37.2794;
    private static final double DEFAULT_LONGITUDE = 127.2274;
    private static final String DEFAULT_AREA_NAME = "월촌교 주변";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter OBSERVED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final GridCoordinateConverter gridCoordinateConverter;
    private final KmaWeatherClient kmaWeatherClient;

    public CurrentWeatherResponse getCurrentWeather(
            CurrentWeatherRequest request
    ) {
        Double latitude = request.getLatitude();
        Double longitude = request.getLongitude();
        if ((latitude == null) != (longitude == null)
                || (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90))
                || (longitude != null && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
        }

        // 1. 위치 결정
        boolean isDefaultLocation =
                latitude == null;

        double targetLat = isDefaultLocation
                ? DEFAULT_LATITUDE
                : latitude;

        double targetLon = isDefaultLocation
                ? DEFAULT_LONGITUDE
                : longitude;

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
                observationTime(weatherData)
        );
    }

    private OffsetDateTime observationTime(Map<String, Integer> weatherData) {
        Integer date = weatherData.get("OBSERVED_DATE");
        Integer time = weatherData.get("OBSERVED_TIME");
        if (weatherData.get("T1H") == null || date == null || time == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.format("%08d%04d", date, time), OBSERVED_TIME_FORMAT)
                    .atZone(KOREA).toOffsetDateTime();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
