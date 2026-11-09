package com.iriro.server.notification.presentation;

import java.time.LocalDateTime;

public record NotificationSettingResponse(boolean recoveryForecastEnabled, LocalDateTime updatedAt) {
}
