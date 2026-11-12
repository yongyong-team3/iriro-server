package com.iriro.server.weather.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CurrentWeatherRequest {

    @Schema(
            description = "위도",
            example = "37.2794"
    )
    private Double latitude;

    @Schema(
            description = "경도",
            example = "127.2274"
    )
    private Double longitude;

    public CurrentWeatherRequest(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
