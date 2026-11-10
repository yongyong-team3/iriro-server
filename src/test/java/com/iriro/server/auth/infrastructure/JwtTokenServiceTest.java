package com.iriro.server.auth.infrastructure;

import com.iriro.server.common.application.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final String TEST_SECRET = "test-only-signing-key-not-for-any-real-environment-123456";

    @Test
    void issuesAndVerifiesTokenWithExpectedType() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        JwtTokenService service = new JwtTokenService(
                TEST_SECRET, 60, 120, Clock.fixed(now, ZoneOffset.UTC)
        );

        JwtTokenService.IssuedToken token = service.issueRefreshToken(42L);

        JwtTokenService.TokenClaims claims = service.verify(token.value(), "refresh");
        assertThat(claims.memberId()).isEqualTo(42L);
        assertThat(claims.type()).isEqualTo("refresh");
        assertThat(service.hash(token.value())).hasSize(64).isNotEqualTo(token.value());
        assertThrows(ApiException.class, () -> service.verify(token.value(), "access"));
    }

    @Test
    void rejectsExpiredAndModifiedTokens() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        JwtTokenService issuer = new JwtTokenService(
                TEST_SECRET, 1, 1, Clock.fixed(now, ZoneOffset.UTC)
        );
        String token = issuer.issueRefreshToken(7L).value();
        JwtTokenService expiredVerifier = new JwtTokenService(
                TEST_SECRET, 60, 60, Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC)
        );

        assertThrows(ApiException.class, () -> expiredVerifier.verify(token, "refresh"));
        assertThrows(ApiException.class, () -> issuer.verify(token + "x", "refresh"));
    }
}
