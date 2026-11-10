package com.iriro.server.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthTokenResponse(
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "eyJ...") String accessToken,
        @Schema(example = "opaque-refresh-token") String refreshToken,
        @Schema(example = "1800") long accessTokenExpiresInSeconds,
        @Schema(example = "2592000") long refreshTokenExpiresInSeconds
) {
}
