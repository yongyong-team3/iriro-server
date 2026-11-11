package com.iriro.server.notification.presentation;

import com.iriro.server.notification.domain.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceRegistrationRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        String installationId,
        @NotNull @Schema(example = "ANDROID") DevicePlatform platform,
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "^(Expo|Exponent)PushToken\\[[^\\[\\]]+\\]$")
        @Schema(example = "ExpoPushToken[example]") String expoPushToken
) {
}
