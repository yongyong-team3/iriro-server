package com.iriro.server.auth.presentation;

import com.iriro.server.auth.domain.SocialAccount;
import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.auth.infrastructure.KakaoSocialProviderClient;
import com.iriro.server.auth.infrastructure.NaverSocialProviderClient;
import com.iriro.server.auth.infrastructure.SocialAccountRepository;
import com.iriro.server.auth.infrastructure.RefreshSessionRepository;
import com.iriro.server.auth.infrastructure.SocialProviderClient.SocialUserInfo;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:social-auth-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Transactional
class SocialAuthApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    SocialAccountRepository socialAccountRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    RefreshSessionRepository refreshSessionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @MockitoBean
    KakaoSocialProviderClient kakaoClient;

    @MockitoBean
    NaverSocialProviderClient naverClient;

    @Test
    void logsInExistingKakaoAccountWithoutCreatingAnotherMember() throws Exception {
        Member member = memberRepository.saveAndFlush(new Member(null, null));
        socialAccountRepository.saveAndFlush(new SocialAccount(member, SocialProvider.KAKAO, "kakao-user-1"));
        long memberCountBefore = memberRepository.count();
        when(kakaoClient.verifyAndGetUser("mock-kakao-token"))
                .thenReturn(new SocialUserInfo("kakao-user-1"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("KAKAO", "mock-kakao-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewMember").value(false))
                .andExpect(jsonPath("$.data.requiresNicknameSetup").value(true))
                .andExpect(jsonPath("$.data.member.memberId").value(member.getId()))
                .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(1800))
                .andExpect(jsonPath("$.data.refreshTokenExpiresInSeconds").value(2592000))
                .andReturn();

        long tokenMemberId = jwtTokenService.verify(extractToken(result, "accessToken"), "access").memberId();
        assertThat(tokenMemberId).isEqualTo(member.getId());
        assertThat(memberRepository.count()).isEqualTo(memberCountBefore);
        assertThat(socialAccountRepository.count()).isEqualTo(1);
    }

    @Test
    void createsAnIndependentMemberForNewNaverIdentity() throws Exception {
        when(naverClient.verifyAndGetUser("mock-naver-token"))
                .thenReturn(new SocialUserInfo("naver-user-1"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("NAVER", "mock-naver-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.isNewMember").value(true))
                .andExpect(jsonPath("$.data.requiresNicknameSetup").value(true))
                .andReturn();

        long memberId = jwtTokenService.verify(extractToken(result, "accessToken"), "access").memberId();
        SocialAccount account = socialAccountRepository
                .findByProviderAndProviderUserId(SocialProvider.NAVER, "naver-user-1")
                .orElseThrow();
        assertThat(account.getMember().getId()).isEqualTo(memberId);
        assertThat(account.getMember().getMemberName()).isNull();
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsProviderTokenFailureWithoutCreatingMember() throws Exception {
        when(kakaoClient.verifyAndGetUser("expired-token"))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PROVIDER_TOKEN", "invalid token"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("KAKAO", "expired-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PROVIDER_TOKEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").value((Object) null));
        assertThat(memberRepository.count()).isZero();
        assertThat(socialAccountRepository.count()).isZero();
        assertThat(refreshSessionRepository.count()).isZero();
    }

    @Test
    void firstLoginForBothProvidersReturnsSpecifiedMemberAndTokenShape() throws Exception {
        when(kakaoClient.verifyAndGetUser("kakao")).thenReturn(new SocialUserInfo("shared-id"));
        when(naverClient.verifyAndGetUser("naver")).thenReturn(new SocialUserInfo("shared-id"));
        for (String[] providerAndToken : new String[][] {{"KAKAO", "kakao"}, {"NAVER", "naver"}}) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson(providerAndToken[0], providerAndToken[1])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("로그인되었습니다."))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.accessTokenExpiresInSeconds").value(1800))
                    .andExpect(jsonPath("$.data.refreshTokenExpiresInSeconds").value(2592000))
                    .andExpect(jsonPath("$.data.isNewMember").value(true))
                    .andExpect(jsonPath("$.data.requiresNicknameSetup").value(true))
                    .andExpect(jsonPath("$.data.member.memberName").value((Object) null))
                    .andExpect(jsonPath("$.data.member.profileImageUrl").value((Object) null));
        }
        assertThat(memberRepository.count()).isEqualTo(2);
    }

    @Test
    void invalidRequestAndProviderOutageUseSpecifiedErrors() throws Exception {
        for (String body : new String[] {
                "{}", requestJson("GOOGLE", "token"), requestJson("KAKAO", "")
        }) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors").value((Object) null));
        }
        when(naverClient.verifyAndGetUser("outage"))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "PROVIDER_UNAVAILABLE", "소셜 로그인 서비스를 사용할 수 없습니다."));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("NAVER", "outage")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));
        assertThat(memberRepository.count()).isZero();
        assertThat(refreshSessionRepository.count()).isZero();
    }

    @Test
    void deletedSocialMemberCreatesNewMemberOnNextLogin() throws Exception {
        when(kakaoClient.verifyAndGetUser("rejoin")).thenReturn(new SocialUserInfo("rejoin-id"));
        String body = requestJson("KAKAO", "rejoin");
        long firstId = memberIdFor(body);
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", firstId);
        long secondId = memberIdFor(body);
        assertThat(secondId).isNotEqualTo(firstId);
    }

    private long memberIdFor(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return jwtTokenService.verify(extractToken(result, "accessToken"), "access").memberId();
    }

    private String requestJson(String provider, String token) {
        return """
                {"provider":"%s","providerToken":"%s"}
                """.formatted(provider, token);
    }

    private String extractToken(MvcResult result, String field) throws Exception {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\":\\\"([^\\\"]+)\\\"")
                .matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new AssertionError("Token field missing from response: " + field);
        }
        return matcher.group(1);
    }
}
