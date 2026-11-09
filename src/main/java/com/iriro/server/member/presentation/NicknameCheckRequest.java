package com.iriro.server.member.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NicknameCheckRequest {

    @NotBlank(message = "닉네임 형식을 확인해 주세요.")
    @Pattern(
            regexp = "^[a-zA-Z0-9가-힣]{2,12}$",
            message = "닉네임 형식을 확인해 주세요."
    )
    private String memberName;
}