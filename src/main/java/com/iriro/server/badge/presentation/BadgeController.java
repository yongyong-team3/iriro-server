package com.iriro.server.badge.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.badge.application.BadgeService;
import com.iriro.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Badge API", description = "배지 관련 API")
@RestController
@RequestMapping("/api/v1/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;
    private final CurrentMemberProvider currentMemberProvider;

    @Operation(summary = "배지 목록 조회", description = "8개 배지의 목표·진행도·획득 여부·획득 시각을 조회합니다. 거리 단위는 m입니다. 코스 지정·다리·하천 산책로 분류가 없는 3종은 진행도 0으로 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "배지 목록 조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED: 인증 실패")
    public ResponseEntity<ApiResponse<BadgeListResponse>> getBadges() {
        Long currentMemberId = currentMemberProvider.requireMemberId();
        BadgeListResponse responseData = badgeService.getMyBadges(currentMemberId);
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", responseData));
    }
}
