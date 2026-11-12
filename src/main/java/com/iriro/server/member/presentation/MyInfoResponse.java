package com.iriro.server.member.presentation;

import com.iriro.server.member.domain.Member;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Builder
public class MyInfoResponse {

    @Schema(example = "1") private Long memberId;
    @Schema(example = "용용이") private String memberName;
    @Schema(example = "user@example.com") private String email;
    @Schema(nullable = true) private String profileImageUrl;

    private long totalActivityCount;
    private long totalDistanceMeters;
    private long earnedBadgeCount;
    private long unreadNotificationCount;

    public static MyInfoResponse of(
            Member member,
            String email,
            long totalActivityCount,
            long totalDistanceMeters,
            long earnedBadgeCount,
            long unreadNotificationCount,
            String profileImageUrl
    ) {
        return MyInfoResponse.builder()
                .memberId(member.getId())
                .memberName(member.getMemberName())
                .email(email)
                .profileImageUrl(profileImageUrl)
                .totalActivityCount(totalActivityCount)
                .totalDistanceMeters(totalDistanceMeters)
                .earnedBadgeCount(earnedBadgeCount)
                .unreadNotificationCount(unreadNotificationCount)
                .build();
    }
}
