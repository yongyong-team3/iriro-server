package com.iriro.server.badge.presentation;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.iriro.server.badge.domain.BadgeCode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class BadgeElementResponse {

    private String code;
    private String name;
    private long target;
    private long progress;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime earnedAt;

    public static BadgeElementResponse of(BadgeCode badgeCode, long progress, LocalDateTime earnedAt) {
        return BadgeElementResponse.builder()
                .code(badgeCode.getCode())
                .name(badgeCode.getName())
                .target(badgeCode.getTarget())
                .progress(progress)
                .earnedAt(earnedAt == null ? null
                        : earnedAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime())
                .build();
    }
}
