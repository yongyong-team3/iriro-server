package com.iriro.server.member.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "닉네임 형식을 확인해 주세요.")
    @Pattern(
            regexp = "^[a-zA-Z0-9가-힣]{2,12}$",
            message = "닉네임 형식을 확인해 주세요."
    )
    private String memberName;

    public UpdateProfileRequest(String memberName) {
        this.memberName = memberName;
    }
}