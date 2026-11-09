package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.common.application.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SocialProviderClientTest {

    @Test
    void verifiesKakaoTokenAndReadsProviderUserIdWithoutCredentials() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kapi.kakao.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoSocialProviderClient client = new KakaoSocialProviderClient(builder.build());
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer mock-kakao-token"))
                .andRespond(withSuccess("{\"id\":123456}", MediaType.APPLICATION_JSON));

        SocialProviderClient.SocialUserInfo profile = client.verifyAndGetUser("mock-kakao-token");

        assertThat(client.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(profile.providerUserId()).isEqualTo("123456");
        server.verify();
    }

    @Test
    void verifiesNaverTokenAndReadsProfileIdWithoutCredentials() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://openapi.naver.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverSocialProviderClient client = new NaverSocialProviderClient(builder.build());
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer mock-naver-token"))
                .andRespond(withSuccess(
                        "{\"resultcode\":\"00\",\"message\":\"success\",\"response\":{\"id\":\"naver-user-1\"}}",
                        MediaType.APPLICATION_JSON
                ));

        SocialProviderClient.SocialUserInfo profile = client.verifyAndGetUser("mock-naver-token");

        assertThat(client.provider()).isEqualTo(SocialProvider.NAVER);
        assertThat(profile.providerUserId()).isEqualTo("naver-user-1");
        server.verify();
    }

    @Test
    void rejectsProviderReportedExpiredToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kapi.kakao.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoSocialProviderClient client = new KakaoSocialProviderClient(builder.build());
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> client.verifyAndGetUser("mock-expired-token")
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getCode()).isEqualTo("INVALID_SOCIAL_TOKEN");
        server.verify();
    }
}
