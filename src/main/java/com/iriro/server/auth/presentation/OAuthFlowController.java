package com.iriro.server.auth.presentation;

import com.iriro.server.auth.application.OAuthFlowService;
import com.iriro.server.common.presentation.ApiSuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증")
@RequestMapping("/api/v1/auth/oauth")
public class OAuthFlowController {
    private final OAuthFlowService service;

    public OAuthFlowController(OAuthFlowService service) {
        this.service = service;
    }

    @GetMapping("/{provider}/authorize")
    @SecurityRequirements
    @Operation(summary = "카카오·네이버 OAuth 인가 시작")
    public ResponseEntity<Void> authorize(@PathVariable String provider, @RequestParam String redirectUri) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(service.authorize(provider, redirectUri)).build();
    }

    @GetMapping("/{provider}/callback")
    @SecurityRequirements
    @Operation(summary = "카카오·네이버 OAuth 콜백")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(service.callback(provider, code, state, error)).build();
    }

    @PostMapping("/exchange")
    @SecurityRequirements
    @Operation(summary = "일회용 로그인 코드 교환")
    public ApiSuccessResponse<SocialLoginResponse> exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        return ApiSuccessResponse.of("SUCCESS", "로그인되었습니다.", service.exchange(request.loginCode()));
    }
}
