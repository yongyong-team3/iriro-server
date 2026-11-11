package com.iriro.server.notification.presentation;

import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationReadResponse(@Schema(example = "1") Long notificationId,
                                       @Schema(example = "2026-11-10T21:00:00+09:00") OffsetDateTime readAt) {
}
