package com.iriro.server.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(@Schema(example = "1") Long memberId,
                           @Schema(example = "user@example.com") String email,
                           @Schema(example = "용용이") String nickname) {
}
