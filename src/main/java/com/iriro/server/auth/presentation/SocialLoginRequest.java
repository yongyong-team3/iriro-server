package com.iriro.server.auth.presentation;

import com.iriro.server.auth.domain.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record SocialLoginRequest(
        @NotNull @Schema(example = "KAKAO", allowableValues = {"KAKAO", "NAVER"}) SocialProvider provider,
        @NotBlank @Schema(example = "provider-access-token") String providerToken
) {
}
