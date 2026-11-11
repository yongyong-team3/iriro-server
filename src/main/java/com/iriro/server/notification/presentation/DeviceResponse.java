package com.iriro.server.notification.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceResponse(@Schema(example = "1") Long deviceId) {
}
