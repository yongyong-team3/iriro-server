package com.iriro.server.auth.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record EmailSignupRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "user@example.com") String email,
        @NotBlank @Schema(example = "password123") String password,
        @NotBlank @Size(min = 2, max = 12) @Schema(example = "용용이") String nickname
) {
}
