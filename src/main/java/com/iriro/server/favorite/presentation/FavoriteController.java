package com.iriro.server.favorite.presentation;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.ApiResponse;
import com.iriro.server.favorite.application.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name="즐겨찾기")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentMemberProvider currentMemberProvider;

    /**
     * FAV-01 즐겨찾기 목록 조회
     */
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

    /**
     * FAV-02 즐겨찾기 추가
     */
    @Operation(
            summary = "즐겨찾기 추가",
            description = "등록 코스를 즐겨찾기에 추가합니다. 이미 저장된 코스라면 기존 즐겨찾기 정보를 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "즐겨찾기 추가 성공 또는 기존 즐겨찾기 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "즐겨찾기 생성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청값 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "코스를 찾을 수 없음"
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<FavoriteCreateResponse>> addFavorite(
            @Valid @RequestBody FavoriteCreateRequest request
    ) {

        FavoriteCreateResponse responseData =
                favoriteService.addFavorite(
                        currentMemberProvider.requireMemberId(),
                        request
                );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("즐겨찾기에 저장되었습니다.", responseData)
        );
    }
    /**
     * FAV-03 즐겨찾기 삭제
     */
    @Operation(
            summary = "즐겨찾기 삭제",
            description = "내가 저장한 코스 즐겨찾기를 해제합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "즐겨찾기 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 실패"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "즐겨찾기를 찾을 수 없음"
            )
    })
    @DeleteMapping("/{favoriteId}")
    public ResponseEntity<Void> deleteFavorite(
            @PathVariable Long favoriteId
    ) {

        favoriteService.deleteFavorite(
                currentMemberProvider.requireMemberId(),
                favoriteId
        );

        return ResponseEntity.noContent().build();
    }
}