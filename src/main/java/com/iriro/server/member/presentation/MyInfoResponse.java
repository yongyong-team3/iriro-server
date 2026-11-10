package com.iriro.server.member.presentation;

import com.iriro.server.member.domain.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyInfoResponse {

    private Long memberId;
    private String memberName;
    private String email;

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
            long unreadNotificationCount
    ) {
        return MyInfoResponse.builder()
                .memberId(member.getId())
                .memberName(member.getMemberName())
                .email(email)
                // .profileImageUrl(member.getProfileImageUrl()) -> 현재 DB에 이미지 URL 컬럼이 없어서 오류 발생중 -> 일시적으로 생략
                .totalActivityCount(totalActivityCount)
                .totalDistanceMeters(totalDistanceMeters)
                .earnedBadgeCount(earnedBadgeCount)
                .unreadNotificationCount(unreadNotificationCount)
                .build();
    }
}