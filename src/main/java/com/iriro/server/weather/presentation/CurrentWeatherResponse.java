
package com.iriro.server.weather.presentation;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.iriro.server.weather.domain.WeatherCondition;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class CurrentWeatherResponse {

    private String areaName;

    private Integer temperatureCelsius;

    private WeatherCondition condition;

    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ssXXX"
    )
    private OffsetDateTime observedAt;

    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ssXXX"
    )
    private OffsetDateTime responseGeneratedAt;

    public static CurrentWeatherResponse of(
            String areaName,
            Integer temperatureCelsius,
            WeatherCondition condition,
            OffsetDateTime observedAt
    ) {
        return CurrentWeatherResponse.builder()
                .areaName(areaName)
                .temperatureCelsius(temperatureCelsius)
                .condition(
                        condition != null
                                ? condition
                                : WeatherCondition.UNKNOWN
                )
                .observedAt(observedAt)
                .responseGeneratedAt(
                        OffsetDateTime.now(
                                ZoneId.of("Asia/Seoul")
                        )
                )
                .build();
    }
}
