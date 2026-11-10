package com.iriro.server.auth.presentation;

import com.iriro.server.auth.application.EmailAuthService;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증")
@RequestMapping("/api/v1/auth")
public class EmailAuthController {

    private final EmailAuthService emailAuthService;

    public EmailAuthController(EmailAuthService emailAuthService) {
        this.emailAuthService = emailAuthService;
    }

    @PostMapping("/signup")
    @SecurityRequirements
    @Operation(summary = "이메일 회원가입")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "SUCCESS: 회원가입 완료"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 입력 오류"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_EMAIL 또는 DUPLICATE_MEMBER_NAME")})
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
    @SecurityRequirements
    @Operation(summary = "이메일 로그인")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "SUCCESS: 토큰과 회원 정보"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 입력 오류"),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: 이메일 또는 비밀번호 오류")})
    public ApiSuccessResponse<SocialLoginResponse> login(@Valid @RequestBody EmailLoginRequest request) {
        return ApiSuccessResponse.of("SUCCESS", "로그인되었습니다.", emailAuthService.login(request));
    }

    @PostMapping("/reissue")
    @SecurityRequirements
    @Operation(summary = "토큰 재발급", description = "Refresh Token을 회전합니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "SUCCESS: 새 토큰 발급"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 요청 형식 오류"),
            @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN 또는 EXPIRED_REFRESH_TOKEN")})
    public ApiSuccessResponse<AuthTokenResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiSuccessResponse.of(
                "SUCCESS",
                "토큰이 재발급되었습니다.",
                emailAuthService.reissue(request.refreshToken())
        );
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "로그아웃", description = "현재 Refresh Token을 만료시킵니다.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "로그아웃 완료, 응답 본문 없음"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 요청 형식 오류")})
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        emailAuthService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
