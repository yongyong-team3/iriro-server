package com.iriro.server.auth.presentation;

import com.iriro.server.auth.application.SocialAuthService;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    public SocialAuthController(SocialAuthService socialAuthService) {
        this.socialAuthService = socialAuthService;
    }

    @PostMapping("/login")
    public ApiSuccessResponse<AuthTokenResponse> login(@Valid @RequestBody SocialLoginRequest request) {
        AuthTokenResponse response = socialAuthService.login(request.provider(), request.accessToken());
        return ApiSuccessResponse.of("SUCCESS", "소셜 로그인이 완료되었습니다.", response);
    }
}
