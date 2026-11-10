package com.iriro.server.notification.presentation;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationSettingUpdateRequest(@NotNull @Schema(example = "true") Boolean recoveryForecastEnabled) {
}
