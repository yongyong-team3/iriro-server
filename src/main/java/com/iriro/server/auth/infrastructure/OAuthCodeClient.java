package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.common.application.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OAuthCodeClient {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper objectMapper;
    private final String kakaoRestKey;
    private final String kakaoSecret;
    private final String naverClientId;
    private final String naverSecret;
    private final String kakaoAuthorizeUrl;
    private final String kakaoTokenUrl;
    private final String naverAuthorizeUrl;
    private final String naverTokenUrl;

    public OAuthCodeClient(ObjectMapper objectMapper,
                           @Value("${iriro.auth.oauth.kakao.rest-api-key:}") String kakaoRestKey,
                           @Value("${iriro.auth.oauth.kakao.client-secret:}") String kakaoSecret,
                           @Value("${iriro.auth.oauth.naver.client-id:}") String naverClientId,
                           @Value("${iriro.auth.oauth.naver.client-secret:}") String naverSecret,
                           @Value("${iriro.auth.oauth.kakao.authorize-url:https://kauth.kakao.com/oauth/authorize}") String kakaoAuthorizeUrl,
                           @Value("${iriro.auth.oauth.kakao.token-url:https://kauth.kakao.com/oauth/token}") String kakaoTokenUrl,
                           @Value("${iriro.auth.oauth.naver.authorize-url:https://nid.naver.com/oauth2.0/authorize}") String naverAuthorizeUrl,
                           @Value("${iriro.auth.oauth.naver.token-url:https://nid.naver.com/oauth2.0/token}") String naverTokenUrl) {
        this.objectMapper = objectMapper;
        this.kakaoRestKey = kakaoRestKey;
        this.kakaoSecret = kakaoSecret;
        this.naverClientId = naverClientId;
        this.naverSecret = naverSecret;
        this.kakaoAuthorizeUrl = kakaoAuthorizeUrl;
        this.kakaoTokenUrl = kakaoTokenUrl;
        this.naverAuthorizeUrl = naverAuthorizeUrl;
        this.naverTokenUrl = naverTokenUrl;
    }

    public URI authorizationUri(SocialProvider provider, String state, String callbackUri) {
        String endpoint = provider == SocialProvider.KAKAO ? kakaoAuthorizeUrl : naverAuthorizeUrl;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", clientId(provider));
        params.put("redirect_uri", callbackUri);
        params.put("state", state);
        return URI.create(endpoint + "?" + form(params));
    }

    public String exchange(SocialProvider provider, String code, String state, String callbackUri) {
        String endpoint = provider == SocialProvider.KAKAO ? kakaoTokenUrl : naverTokenUrl;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("client_id", clientId(provider));
        params.put("redirect_uri", callbackUri);
        params.put("code", code);
        if (provider == SocialProvider.KAKAO && !kakaoSecret.isBlank()) params.put("client_secret", kakaoSecret);
        if (provider == SocialProvider.NAVER) {
            params.put("client_secret", required(naverSecret));
            params.put("state", state);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(form(params), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw providerUnavailable();
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode accessToken = body.path("access_token");
            if (!accessToken.isTextual() || accessToken.asText().isBlank()) throw providerUnavailable();
            return accessToken.asText();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerUnavailable();
        } catch (Exception exception) {
            if (exception instanceof ApiException apiException) throw apiException;
            throw providerUnavailable();
        }
    }

    private String clientId(SocialProvider provider) {
        return required(provider == SocialProvider.KAKAO ? kakaoRestKey : naverClientId);
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_NOT_CONFIGURED",
                    "소셜 로그인이 아직 설정되지 않았습니다.");
        }
        return value;
    }

    private String form(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private ApiException providerUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE",
                "소셜 로그인 서비스를 사용할 수 없습니다.");
    }
}
