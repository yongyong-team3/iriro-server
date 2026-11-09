package com.iriro.server.auth.presentation;

public record AuthTokenResponse(
        String tokenType,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
