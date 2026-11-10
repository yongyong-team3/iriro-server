package com.iriro.server.notification.presentation;

import com.iriro.server.notification.domain.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceRegistrationRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String installationId,
        @NotNull DevicePlatform platform,
        @NotBlank @Size(max = 255) String expoPushToken
) {
}
