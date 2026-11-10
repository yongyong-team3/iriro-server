package com.iriro.server.notification.presentation;

import com.iriro.server.notification.domain.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceResponse(@Schema(example = "1") Long deviceId,
                             @Schema(example = "550e8400-e29b-41d4-a716-446655440000") String installationId,
                             @Schema(example = "ANDROID") DevicePlatform platform) {
}
