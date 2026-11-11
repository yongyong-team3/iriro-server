package com.iriro.server.auth.presentation;

import com.iriro.server.member.domain.NicknamePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record EmailSignupRequest(
        @NotBlank @Pattern(regexp = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$")
        @Size(max = 256) @Schema(example = "user@example.com") String email,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$")
        @Schema(example = "abc12345") String password,
        @NotBlank @Pattern(regexp = NicknamePolicy.REGEXP)
        @Schema(example = "용용이", description = "한글·영문·숫자 2~12자") String memberName
) {
}
