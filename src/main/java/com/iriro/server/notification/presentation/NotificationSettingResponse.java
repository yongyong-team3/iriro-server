package com.iriro.server.notification.presentation;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationSettingResponse(@Schema(example = "true") boolean recoveryForecastEnabled,
                                          @Schema(example = "2026-11-10T12:00:00") LocalDateTime updatedAt) {
}
