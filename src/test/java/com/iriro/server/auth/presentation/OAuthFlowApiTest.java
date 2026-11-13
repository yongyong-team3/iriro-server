package com.iriro.server.auth.presentation;

import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.auth.infrastructure.KakaoSocialProviderClient;
import com.iriro.server.auth.infrastructure.NaverSocialProviderClient;
import com.iriro.server.auth.infrastructure.OAuthCodeClient;
import com.iriro.server.auth.infrastructure.SocialProviderClient.SocialUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:oauth-flow-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "iriro.auth.oauth.callback-base-url=https://api.example.test",
        "iriro.auth.oauth.allowed-redirect-prefixes=exp://,cheonbyeon://"
})
@Transactional
class OAuthFlowApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OAuthCodeClient oauthCodeClient;
    @MockitoBean KakaoSocialProviderClient kakaoClient;
    @MockitoBean NaverSocialProviderClient naverClient;

    @Test
    void rejectsRedirectOutsideAllowedPrefixesAndUnknownProvider() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/authorize"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/authorize")
                        .param("redirectUri", "https://evil.example/callback"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REDIRECT_URI"));
        mockMvc.perform(get("/api/v1/auth/oauth/google/authorize")
                        .param("redirectUri", "cheonbyeon://oauth/callback"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM oauth_states", Integer.class)).isZero();
    }

    @Test
    void rejectsMismatchedExpiredAndReusedStateBeforeTokenExchange() throws Exception {
        String state = authorize("kakao", "cheonbyeon://oauth/callback");
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "provider-code").param("state", "wrong"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"));
        mockMvc.perform(get("/api/v1/auth/oauth/naver/callback")
                        .param("code", "provider-code").param("state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"));
        jdbc.update("UPDATE oauth_states SET expires_at = ?", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "provider-code").param("state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"));
        verify(oauthCodeClient, never()).exchange(eq(SocialProvider.KAKAO), anyString(), anyString(), anyString());
    }

    @Test
    void successfulKakaoCallbackAndExchangeUsesExistingSocialLoginShapeOnce() throws Exception {
        when(oauthCodeClient.exchange(eq(SocialProvider.KAKAO), eq("provider-code"), anyString(),
                eq("https://api.example.test/api/v1/auth/oauth/kakao/callback")))
                .thenReturn("provider-access-token");
        when(kakaoClient.verifyAndGetUser("provider-access-token"))
                .thenReturn(new SocialUserInfo("kakao-oauth-user"));
        String state = authorize("kakao", "cheonbyeon://oauth/callback?source=expo");
        String callbackLocation = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "provider-code").param("state", state))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(callbackLocation).startsWith("cheonbyeon://oauth/callback?source=expo&loginCode=")
                .doesNotContain("provider-access-token", "refreshToken", "accessToken");
        String loginCode = queryValue(callbackLocation, "loginCode");
        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginCode\":\"" + loginCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", aMapWithSize(8)))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(1800))
                .andExpect(jsonPath("$.data.refreshTokenExpiresInSeconds").value(2592000))
                .andExpect(jsonPath("$.data.isNewMember").value(true))
                .andExpect(jsonPath("$.data.requiresNicknameSetup").value(true))
                .andExpect(jsonPath("$.data.member.memberId").isNumber())
                .andExpect(jsonPath("$.data.member", aMapWithSize(3)))
                .andExpect(jsonPath("$.data.member.memberName").value((Object) null))
                .andExpect(jsonPath("$.data.member.profileImageUrl").value((Object) null));
        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginCode\":\"" + loginCode + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN_CODE"));
        mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("code", "provider-code").param("state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"));
    }

    @Test
    void expiredLoginCodeAndProviderDenialReturnSafeFailures() throws Exception {
        when(oauthCodeClient.exchange(eq(SocialProvider.NAVER), eq("provider-code"), anyString(),
                eq("https://api.example.test/api/v1/auth/oauth/naver/callback")))
                .thenReturn("naver-access-token");
        when(naverClient.verifyAndGetUser("naver-access-token"))
                .thenReturn(new SocialUserInfo("naver-oauth-user"));
        String state = authorize("naver", "exp://127.0.0.1:8081/--/oauth");
        String location = mockMvc.perform(get("/api/v1/auth/oauth/naver/callback")
                        .param("code", "provider-code").param("state", state))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        jdbc.update("UPDATE oauth_login_codes SET expires_at = ?", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        mockMvc.perform(post("/api/v1/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginCode\":\"" + queryValue(location, "loginCode") + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN_CODE"));
        String deniedState = authorize("kakao", "cheonbyeon://oauth/callback");
        String deniedLocation = mockMvc.perform(get("/api/v1/auth/oauth/kakao/callback")
                        .param("error", "access_denied").param("state", deniedState))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(deniedLocation).isEqualTo("cheonbyeon://oauth/callback?error=OAUTH_DENIED");
    }

    private String authorize(String provider, String redirectUri) throws Exception {
        SocialProvider parsed = SocialProvider.valueOf(provider.toUpperCase());
        when(oauthCodeClient.authorizationUri(eq(parsed), anyString(), anyString())).thenAnswer(invocation ->
                URI.create("https://provider.example.test/authorize?state=" + invocation.getArgument(1)));
        String location = mockMvc.perform(get("/api/v1/auth/oauth/{provider}/authorize", provider)
                        .param("redirectUri", redirectUri))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        return queryValue(location, "state");
    }

    private String queryValue(String uri, String key) {
        String query = URI.create(uri).getRawQuery();
        for (String entry : query.split("&")) {
            String[] parts = entry.split("=", 2);
            if (parts[0].equals(key)) return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("missing " + key);
    }
}
