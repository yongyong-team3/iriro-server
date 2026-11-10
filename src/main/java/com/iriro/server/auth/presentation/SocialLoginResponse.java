package com.iriro.server.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

public record SocialLoginResponse(
        @Schema(example = "eyJ...") String accessToken,
        @Schema(example = "opaque-refresh-token") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "1800") long accessTokenExpiresInSeconds,
        @Schema(example = "2592000") long refreshTokenExpiresInSeconds,
        boolean isNewMember,
        boolean requiresNicknameSetup,
        MemberInfo member
) {
    public record MemberInfo(@Schema(example = "1") Long memberId, String memberName, String profileImageUrl) { }
}
