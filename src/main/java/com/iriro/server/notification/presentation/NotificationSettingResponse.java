package com.iriro.server.notification.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationSettingResponse(@Schema(example = "true") boolean recoveryForecastEnabled) {
}
