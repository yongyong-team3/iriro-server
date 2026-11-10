package com.iriro.server.badge.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 배지 목표 및 정보 정의 Enum
 */
@Getter
@RequiredArgsConstructor
public enum BadgeCode {

    FIRST_ACTIVITY("FIRST_ACTIVITY", "첫 활동", 1L),
    FIVE_ACTIVITIES("FIVE_ACTIVITIES", "활동 5회", 5L),
    TEN_KM("TEN_KM", "누적 10km", 10000L); // 단위: m (10km = 10,000m)

    private final String code;
    private final String name;
    private final long target;
}