package com.iriro.server.weather.presentation;

import com.iriro.server.common.ApiResponse;
import com.iriro.server.weather.application.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Weather API", description = "날씨 관련 API")
@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @Operation(summary = "현재 날씨 조회", description = "현재 위치의 날씨 및 기온을 조회합니다. 좌표 생략 시 월촌교 좌표를 사용합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "현재 날씨 조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 좌표 오류")
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<CurrentWeatherResponse>> getCurrentWeather(
            @Valid @ModelAttribute CurrentWeatherRequest request
    ) {
        CurrentWeatherResponse responseData =
                weatherService.getCurrentWeather(request);

        return ResponseEntity.ok(
                ApiResponse.success("조회되었습니다.", responseData)
        );
    }
}
