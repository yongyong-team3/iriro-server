package com.iriro.server.auth.application;

import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.auth.infrastructure.KakaoSocialProviderClient;
import com.iriro.server.auth.infrastructure.NaverSocialProviderClient;
import com.iriro.server.auth.infrastructure.SocialProviderClient;
import com.iriro.server.auth.presentation.AuthTokenResponse;
import com.iriro.server.common.application.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SocialAuthService {

    private final KakaoSocialProviderClient kakaoClient;
    private final NaverSocialProviderClient naverClient;
    private final SocialAccountLoginService socialAccountLoginService;

    public SocialAuthService(
            KakaoSocialProviderClient kakaoClient,
            NaverSocialProviderClient naverClient,
            SocialAccountLoginService socialAccountLoginService
    ) {
        this.kakaoClient = kakaoClient;
        this.naverClient = naverClient;
        this.socialAccountLoginService = socialAccountLoginService;
    }

    public AuthTokenResponse login(SocialProvider provider, String accessToken) {
        SocialProviderClient.SocialUserInfo user = switch (provider) {
            case KAKAO -> kakaoClient.verifyAndGetUser(accessToken);
            case NAVER -> naverClient.verifyAndGetUser(accessToken);
        };
        if (user == null || user.providerUserId() == null || user.providerUserId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SOCIAL_PROVIDER_UNAVAILABLE", "소셜 사용자 정보를 확인할 수 없습니다.");
        }
        return socialAccountLoginService.login(provider, user.providerUserId());
    }
}
