package com.iriro.server.auth.application;

import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.auth.infrastructure.OAuthCodeClient;
import com.iriro.server.auth.infrastructure.OAuthFlowStore;
import com.iriro.server.auth.presentation.SocialLoginResponse;
import com.iriro.server.common.application.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class OAuthFlowService {
    private final OAuthFlowStore store;
    private final OAuthCodeClient codeClient;
    private final SocialAuthService socialAuthService;
    private final SocialAccountLoginService accountLoginService;
    private final String callbackBaseUrl;
    private final List<String> allowedRedirectPrefixes;

    public OAuthFlowService(OAuthFlowStore store, OAuthCodeClient codeClient,
                            SocialAuthService socialAuthService, SocialAccountLoginService accountLoginService,
                            @Value("${iriro.auth.oauth.callback-base-url:}") String callbackBaseUrl,
                            @Value("${iriro.auth.oauth.allowed-redirect-prefixes:exp://,cheonbyeon://}")
                            String allowedRedirectPrefixes) {
        this.store = store;
        this.codeClient = codeClient;
        this.socialAuthService = socialAuthService;
        this.accountLoginService = accountLoginService;
        this.callbackBaseUrl = callbackBaseUrl;
        this.allowedRedirectPrefixes = Arrays.stream(allowedRedirectPrefixes.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    public URI authorize(String providerValue, String redirectUri) {
        SocialProvider provider = provider(providerValue);
        validateRedirectUri(redirectUri);
        String callbackUri = callbackUri(provider);
        String state = store.createState(provider, redirectUri);
        return codeClient.authorizationUri(provider, state, callbackUri);
    }

    public URI callback(String providerValue, String code, String state, String providerError) {
        SocialProvider provider = provider(providerValue);
        String redirectUri = store.consumeState(provider, state);
        validateRedirectUri(redirectUri);
        if (providerError != null) return appRedirect(redirectUri, "error", "OAUTH_DENIED");
        if (code == null || code.isBlank()) return appRedirect(redirectUri, "error", "OAUTH_FAILED");
        try {
            String accessToken = codeClient.exchange(provider, code, state, callbackUri(provider));
            String providerUserId = socialAuthService.verifiedUserId(provider, accessToken);
            var prepared = accountLoginService.prepare(provider, providerUserId);
            String loginCode = store.createLoginCode(prepared);
            return appRedirect(redirectUri, "loginCode", loginCode);
        } catch (ApiException exception) {
            return appRedirect(redirectUri, "error", exception.getCode());
        } catch (RuntimeException exception) {
            log.error("OAuth callback failed provider={} type={}", provider, exception.getClass().getSimpleName());
            return appRedirect(redirectUri, "error", "OAUTH_FAILED");
        }
    }

    public SocialLoginResponse exchange(String loginCode) {
        return accountLoginService.complete(store.consumeLoginCode(loginCode));
    }

    private SocialProvider provider(String value) {
        if (value == null) throw invalidRequest();
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "kakao" -> SocialProvider.KAKAO;
            case "naver" -> SocialProvider.NAVER;
            default -> throw invalidRequest();
        };
    }

    private void validateRedirectUri(String value) {
        if (value == null || value.isBlank() || value.length() > 2048
                || allowedRedirectPrefixes.stream().noneMatch(value::startsWith)) throw invalidRedirect();
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()
                    || uri.getRawFragment() != null || uri.getRawUserInfo() != null
                    || value.chars().anyMatch(Character::isISOControl)) throw invalidRedirect();
            String query = uri.getRawQuery();
            if (query != null && Arrays.stream(query.split("&"))
                    .map(part -> URLDecoder.decode(part.split("=", 2)[0], StandardCharsets.UTF_8))
                    .anyMatch(part -> part.equals("loginCode") || part.equals("error"))) throw invalidRedirect();
        } catch (IllegalArgumentException exception) {
            throw invalidRedirect();
        }
    }

    private String callbackUri(SocialProvider provider) {
        try {
            URI base = URI.create(callbackBaseUrl);
            if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                    || base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null
                    || (base.getRawPath() != null && !base.getRawPath().isEmpty()
                    && !"/".equals(base.getRawPath()))) throw new IllegalArgumentException();
            return callbackBaseUrl.replaceAll("/+$", "") + "/api/v1/auth/oauth/"
                    + provider.name().toLowerCase(Locale.ROOT) + "/callback";
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_NOT_CONFIGURED",
                    "소셜 로그인이 아직 설정되지 않았습니다.");
        }
    }

    private URI appRedirect(String redirectUri, String key, String value) {
        return URI.create(redirectUri + (redirectUri.contains("?") ? "&" : "?")
                + key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private ApiException invalidRequest() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
    }

    private ApiException invalidRedirect() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REDIRECT_URI", "앱 복귀 주소를 확인해 주세요.");
    }
}
