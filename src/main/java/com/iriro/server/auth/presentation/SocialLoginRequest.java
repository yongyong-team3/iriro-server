package com.iriro.server.auth.presentation;

import com.iriro.server.auth.domain.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialLoginRequest(
        @NotNull SocialProvider provider,
        @NotBlank String accessToken
) {
}
