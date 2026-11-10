package com.iriro.server.auth.presentation;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshTokenRequest(@NotBlank @Schema(example = "opaque-refresh-token") String refreshToken) {
}
