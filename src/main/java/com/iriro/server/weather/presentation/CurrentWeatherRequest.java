package com.iriro.server.weather.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CurrentWeatherRequest {

    @Min(value = -90, message = "요청 정보를 확인해 주세요.")
    @Max(value = 90, message = "요청 정보를 확인해 주세요.")
    private Double latitude;

    @Min(value = -180, message = "요청 정보를 확인해 주세요.")
    @Max(value = 180, message = "요청 정보를 확인해 주세요.")
    private Double longitude;

    public CurrentWeatherRequest(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}