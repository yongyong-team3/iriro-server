package com.iriro.server.member.presentation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NicknameCheckResponse {

    private String memberName;
    private boolean available;

    public static NicknameCheckResponse of(String memberName, boolean available) {
        return NicknameCheckResponse.builder()
                .memberName(memberName)
                .available(available)
                .build();
    }
}