package com.iriro.server.auth.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailLoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String password
) {
}
