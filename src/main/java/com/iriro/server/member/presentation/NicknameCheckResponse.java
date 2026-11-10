package com.iriro.server.member.presentation;

import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
public class NicknameCheckResponse {

    @Schema(example = "용용이") private String memberName;
    @Schema(example = "true") private boolean available;

    public static NicknameCheckResponse of(String memberName, boolean available) {
        return NicknameCheckResponse.builder()
                .memberName(memberName)
                .available(available)
                .build();
    }
}
