package com.iriro.server.badge.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배지 목표 및 정보 정의 Enum
 */
@Getter
@RequiredArgsConstructor
public enum BadgeCode {

    COURSE_FINISHER("COURSE_FINISHER", "코스 완주자", 3L),
    RIVER_BRIDGE("RIVER_BRIDGE", "하천 브릿지", 10L),
    RIVER_WALKWAY("RIVER_WALKWAY", "하천 산책로", 15L),
    WALK_LOVER("WALK_LOVER", "산책 러버", 20L),
    YONGIN_CITY_EXPLORER("YONGIN_CITY_EXPLORER", "용인 도시탐험", 3L),
    BIKE_RIDER("BIKE_RIDER", "자전거 라이더", 100000L),
    CONSISTENT_ACTIVIST("CONSISTENT_ACTIVIST", "꾸준한 활동가", 30L),
    YONGIN_DRAGON("YONGIN_DRAGON", "용인 드래곤", 200000L); // 거리 목표 단위: m

    private final String code;
    private final String name;
    private final long target;
}
