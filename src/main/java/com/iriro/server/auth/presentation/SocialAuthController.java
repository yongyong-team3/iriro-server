package com.iriro.server.auth.presentation;

import com.iriro.server.auth.application.SocialAuthService;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증")
@RequestMapping("/api/v1/auth")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    public SocialAuthController(SocialAuthService socialAuthService) {
        this.socialAuthService = socialAuthService;
    }

    @PostMapping("/login")
    @Operation(summary = "카카오·네이버 로그인", description = "앱에서 받은 제공자 Access Token으로 로그인합니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "토큰과 회원 정보"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류"),
            @ApiResponse(responseCode = "401", description = "제공자 토큰 오류"),
            @ApiResponse(responseCode = "409", description = "계정 처리 충돌"),
            @ApiResponse(responseCode = "503", description = "제공자 인증 서버 장애")})
    public ApiSuccessResponse<SocialLoginResponse> login(@Valid @RequestBody SocialLoginRequest request) {
        SocialLoginResponse response = socialAuthService.login(request.provider(), request.providerToken());
        return ApiSuccessResponse.of("SUCCESS", "소셜 로그인이 완료되었습니다.", response);
    }
}
