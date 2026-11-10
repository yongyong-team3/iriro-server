package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.common.application.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import lombok.extern.slf4j.Slf4j;
import java.util.regex.Pattern;

@Component
@Slf4j
public class KakaoSocialProviderClient implements SocialProviderClient {

    private static final Pattern TEMPORARY_PROVIDER_ERROR = Pattern.compile("\\\"code\\\"\\s*:\\s*-1\\b");
    private final RestClient restClient;
    private final long appId;

    @Autowired
    public KakaoSocialProviderClient(
            @Value("${auth.kakao.base-url}") String baseUrl,
            @Value("${iriro.auth.kakao.app-id}") long appId
    ) {
        this(RestClient.create(baseUrl), appId);
    }

    public KakaoSocialProviderClient(RestClient restClient, long appId) {
        this.restClient = restClient;
        this.appId = appId;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialUserInfo verifyAndGetUser(String accessToken) {
        try {
            KakaoTokenInfo tokenInfo = restClient.get()
                    .uri("/v1/user/access_token_info")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoTokenInfo.class);
            if (tokenInfo == null || tokenInfo.id() == null || tokenInfo.appId() == null
                    || tokenInfo.expiresIn() == null) {
                throw providerUnavailable();
            }
            if (tokenInfo.appId() != appId || tokenInfo.expiresIn() <= 0) {
                throw invalidProviderToken();
            }
            KakaoUserResponse response = restClient.get()
                    .uri("/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (response == null || response.id() == null) {
                throw providerUnavailable();
            }
            if (!response.id().equals(tokenInfo.id())) {
                throw invalidProviderToken();
            }
            return new SocialUserInfo(response.id().toString());
        } catch (RestClientResponseException exception) {
            log.warn("social provider=KAKAO result=http_error status={}", exception.getStatusCode().value());
            if (exception.getStatusCode().value() == 400
                    && TEMPORARY_PROVIDER_ERROR.matcher(exception.getResponseBodyAsString()).find()) {
                throw providerUnavailable();
            }
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 401) {
                throw invalidProviderToken();
            }
            throw providerUnavailable();
        } catch (RestClientException exception) {
            log.warn("social provider=KAKAO result=connection_error");
            throw providerUnavailable();
        }
    }

    private ApiException invalidProviderToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PROVIDER_TOKEN", "소셜 인증 정보가 유효하지 않습니다.");
    }

    private ApiException providerUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", "소셜 로그인 서비스를 사용할 수 없습니다.");
    }

    private record KakaoUserResponse(Long id) { }

    private record KakaoTokenInfo(Long id,
                                  @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn,
                                  @com.fasterxml.jackson.annotation.JsonProperty("app_id") Long appId) { }
}
