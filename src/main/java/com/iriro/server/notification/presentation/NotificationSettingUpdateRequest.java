package com.iriro.server.notification.presentation;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingUpdateRequest(@NotNull Boolean recoveryForecastEnabled) {
}
