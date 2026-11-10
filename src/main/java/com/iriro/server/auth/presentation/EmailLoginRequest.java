package com.iriro.server.auth.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record EmailLoginRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "user@example.com") String email,
        @NotBlank @Schema(example = "password123") String password
) {
}
