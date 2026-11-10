package com.iriro.server.badge.presentation;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BadgeListResponse {

    private long earnedCount;
    private long totalCount;
    private List<BadgeElementResponse> badges;

    public static BadgeListResponse of(long earnedCount, List<BadgeElementResponse> badges) {
        return BadgeListResponse.builder()
                .earnedCount(earnedCount)
                .totalCount(badges.size())
                .badges(badges)
                .build();
    }
}