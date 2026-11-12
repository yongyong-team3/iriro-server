package com.iriro.server.member.presentation;

import com.iriro.server.member.domain.Member;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
public class UpdateProfileResponse {

    @Schema(example = "1") private Long memberId;
    @Schema(example = "용용이") private String memberName;
    @Schema(nullable = true) private String profileImageUrl;

    public static UpdateProfileResponse from(Member member, String profileImageUrl) {
        return UpdateProfileResponse.builder()
                .memberId(member.getId())
                .memberName(member.getMemberName())
                .profileImageUrl(profileImageUrl)
                .build();
    }
}
