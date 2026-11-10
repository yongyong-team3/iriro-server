package com.iriro.server.notification.presentation;

import com.iriro.server.notification.domain.DevicePlatform;

public record DeviceResponse(Long deviceId, String installationId, DevicePlatform platform) {
}
