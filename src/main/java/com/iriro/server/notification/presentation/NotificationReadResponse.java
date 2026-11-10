package com.iriro.server.notification.presentation;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationReadResponse(@Schema(example = "1") Long notificationId,
                                       @Schema(example = "2026-11-10T12:00:00") LocalDateTime readAt) {
}
