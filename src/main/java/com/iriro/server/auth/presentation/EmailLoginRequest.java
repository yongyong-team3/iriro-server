package com.iriro.server.auth.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record EmailLoginRequest(
        @NotBlank @Pattern(regexp = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$")
        @Size(max = 256) @Schema(example = "user@example.com") String email,
        @NotBlank @Schema(example = "password123") String password
) {
}
