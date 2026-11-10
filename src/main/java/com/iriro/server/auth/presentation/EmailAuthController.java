package com.iriro.server.auth.presentation;

import com.iriro.server.auth.application.EmailAuthService;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class EmailAuthController {

    private final EmailAuthService emailAuthService;

    public EmailAuthController(EmailAuthService emailAuthService) {
        this.emailAuthService = emailAuthService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiSuccessResponse<AuthResponse>> signup(
            @Valid @RequestBody EmailSignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiSuccessResponse.of(
                "SUCCESS",
                "회원가입이 완료되었습니다.",
                emailAuthService.signup(request)
        ));
    }

    @PostMapping("/email/login")
    public ApiSuccessResponse<AuthTokenResponse> login(@Valid @RequestBody EmailLoginRequest request) {
        return ApiSuccessResponse.of("SUCCESS", "로그인이 완료되었습니다.", emailAuthService.login(request));
    }

    @PostMapping("/reissue")
    public ApiSuccessResponse<AuthTokenResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiSuccessResponse.of(
                "SUCCESS",
                "토큰이 재발급되었습니다.",
                emailAuthService.reissue(request.refreshToken())
        );
    }

    @PostMapping("/logout")
    public ApiSuccessResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        emailAuthService.logout(request.refreshToken());
        return ApiSuccessResponse.of("SUCCESS", "로그아웃되었습니다.", null);
    }
}
