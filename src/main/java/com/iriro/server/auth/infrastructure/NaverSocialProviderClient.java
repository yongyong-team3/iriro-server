package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.common.application.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NaverSocialProviderClient implements SocialProviderClient {

    private final RestClient restClient;

    @Autowired
    public NaverSocialProviderClient() {
        this(RestClient.create("https://openapi.naver.com"));
    }

    public NaverSocialProviderClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    public SocialUserInfo verifyAndGetUser(String accessToken) {
        try {
            NaverUserResponse response = restClient.get()
                    .uri("/v1/nid/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(NaverUserResponse.class);
            if (response == null || !"00".equals(response.resultcode())
                    || response.response() == null || response.response().id() == null
                    || response.response().id().isBlank()) {
                throw providerUnavailable();
            }
            return new SocialUserInfo(response.response().id());
        } catch (RestClientResponseException exception) {
            log.warn("social provider=NAVER result=http_error status={}", exception.getStatusCode().value());
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 401) {
                throw invalidProviderToken();
            }
            throw providerUnavailable();
        } catch (RestClientException exception) {
            log.warn("social provider=NAVER result=connection_error");
            throw providerUnavailable();
        }
    }

    private ApiException invalidProviderToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PROVIDER_TOKEN", "소셜 인증 정보가 유효하지 않습니다.");
    }

    private ApiException providerUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", "소셜 로그인 서비스를 사용할 수 없습니다.");
    }

    private record NaverUserResponse(String resultcode, NaverProfile response) { }

    private record NaverProfile(String id) { }
}
