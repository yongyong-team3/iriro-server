package com.iriro.server.badge.presentation;

import com.iriro.server.badge.application.BadgeService;
import com.iriro.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Badge API", description = "배지 관련 API")
@RestController
@RequestMapping("/api/v1/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    @Operation(summary = "배지 목록 조회", description = "배지 획득 현황을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<ApiResponse<BadgeListResponse>> getBadges(
            @RequestAttribute("memberId") Long currentMemberId
    ) {
        BadgeListResponse responseData = badgeService.getMyBadges(currentMemberId);
        return ResponseEntity.ok(ApiResponse.success("조회되었습니다.", responseData));
    }
}