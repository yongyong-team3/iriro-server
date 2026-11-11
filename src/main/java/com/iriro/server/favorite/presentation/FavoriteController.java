package com.iriro.server.favorite.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.ApiResponse;
import com.iriro.server.favorite.application.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentMemberProvider currentMemberProvider;

    @Operation(
            summary = "즐겨찾기 목록 조회",
            description = "내가 즐겨찾기한 등록 코스 목록을 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "즐겨찾기 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<FavoriteListResponse>> getFavorites() {

        FavoriteListResponse responseData =
                favoriteService.getFavorites(
                        currentMemberProvider.requireMemberId()
                );

        return ResponseEntity.ok(
                ApiResponse.success("조회되었습니다.", responseData)
        );
    }
}