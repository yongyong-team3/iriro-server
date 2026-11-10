
package com.iriro.server.weather.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WeatherCondition {

    SUNNY("맑음"),
    CLOUDY("구름많음/흐림"),
    RAINY("비"),
    SNOWY("눈/진눈깨비"),
    UNKNOWN("확인 불가");

    private final String description;

    /**
     * PTY: 강수형태
     * SKY: 하늘상태
     */
    public static WeatherCondition of(Integer ptyCode, Integer skyCode) {

        // 1. 강수형태 우선 판단
        if (ptyCode != null) {
            switch (ptyCode) {
                case 0:
                    break;
                case 1, 4, 5:
                    return RAINY;
                case 2, 3, 6, 7:
                    return SNOWY;
                default:
                    return UNKNOWN;
            }
        }

        // 2. 강수가 없을 경우 하늘상태 판단
        if (skyCode != null) {
            return switch (skyCode) {
                case 1 -> SUNNY;
                case 3, 4 -> CLOUDY;
                default -> UNKNOWN;
            };
        }

        return UNKNOWN;
    }
}
