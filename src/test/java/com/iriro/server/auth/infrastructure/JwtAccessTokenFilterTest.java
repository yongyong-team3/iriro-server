package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.member.infrastructure.MemberRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAccessTokenFilterTest {

    private static final String TEST_SECRET = "test-only-signing-key-not-for-any-real-environment-123456";

    @Mock
    MemberRepository memberRepository;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void acceptsAccessTokenAndExposesMemberIdToRequestConsumers() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        JwtTokenService tokens = new JwtTokenService(TEST_SECRET, 60, 120, Clock.fixed(now, ZoneOffset.UTC));
        String accessToken = tokens.issueAccessToken(42L).value();
        when(memberRepository.existsById(42L)).thenReturn(true);
        JwtAccessTokenFilter filter = new JwtAccessTokenFilter(tokens, memberRepository);
        MockHttpServletRequest request = requestWith(accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(CurrentMemberProvider.MEMBER_ID_ATTRIBUTE)).isEqualTo(42L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(new CurrentMemberProvider().requireMemberId()).isEqualTo(42L);
        verify(memberRepository).existsById(42L);
    }

    @Test
    void rejectsTamperedExpiredRefreshAndUnknownMemberTokens() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        JwtTokenService issuer = new JwtTokenService(TEST_SECRET, 1, 120, Clock.fixed(now, ZoneOffset.UTC));
        JwtAccessTokenFilter validTimeFilter = new JwtAccessTokenFilter(issuer, memberRepository);

        assertUnauthorized(validTimeFilter, requestWith(issuer.issueAccessToken(5L).value() + "x"));
        assertUnauthorized(validTimeFilter, requestWith(issuer.issueRefreshToken(5L).value()));

        JwtTokenService expiredVerifier = new JwtTokenService(
                TEST_SECRET, 60, 60, Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC)
        );
        assertUnauthorized(
                new JwtAccessTokenFilter(expiredVerifier, memberRepository),
                requestWith(issuer.issueAccessToken(5L).value())
        );

        when(memberRepository.existsById(7L)).thenReturn(false);
        assertUnauthorized(
                new JwtAccessTokenFilter(issuer, memberRepository),
                requestWith(issuer.issueAccessToken(7L).value())
        );
    }

    @Test
    void allowsPublicRequestsWithoutBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        JwtAccessTokenFilter filter = new JwtAccessTokenFilter(
                new JwtTokenService(TEST_SECRET, 60, 120, Clock.systemUTC()), memberRepository
        );

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private void assertUnauthorized(JwtAccessTokenFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED", "\"errors\":null");
    }

    private MockHttpServletRequest requestWith(String accessToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        return request;
    }
}
