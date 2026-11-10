package com.iriro.server.member.presentation;

import com.iriro.server.member.domain.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateProfileResponse {

    private Long memberId;
    private String memberName;
    private String profileImageUrl;

    public static UpdateProfileResponse from(Member member) {
        return UpdateProfileResponse.builder()
                .memberId(member.getId())
                .memberName(member.getMemberName())
                // .profileImageUrl(member.getProfileImageUrl()) -> 추후 추가
                .build();
    }
}