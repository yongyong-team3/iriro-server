package com.iriro.server.auth.infrastructure;

import com.iriro.server.common.application.ApiException;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JwtTokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Pattern MEMBER_ID = Pattern.compile("\\\"sub\\\":([0-9]+)");
    private static final Pattern TOKEN_TYPE = Pattern.compile("\\\"typ\\\":\\\"(access|refresh)\\\"");
    private static final Pattern EXPIRATION = Pattern.compile("\\\"exp\\\":([0-9]+)");

    private final SecretKeySpec key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenService(String secret, long accessTtlSeconds, long refreshTtlSeconds, Clock clock) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("iriro.auth.jwt-secret must contain at least 32 UTF-8 bytes");
        }
        if (accessTtlSeconds <= 0 || refreshTtlSeconds <= 0) {
            throw new IllegalArgumentException("JWT token lifetimes must be positive");
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.clock = clock;
    }

    public IssuedToken issueAccessToken(long memberId) {
        return issue(memberId, "access", accessTtlSeconds);
    }

    public IssuedToken issueRefreshToken(long memberId) {
        return issue(memberId, "refresh", refreshTtlSeconds);
    }

    public TokenClaims verify(String token, String expectedType) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !HEADER.equals(new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8))) {
                throw invalidToken();
            }
            byte[] actualSignature = DECODER.decode(parts[2]);
            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
                throw invalidToken();
            }
            String payload = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            long memberId = captureLong(MEMBER_ID, payload);
            long expiration = captureLong(EXPIRATION, payload);
            String type = capture(TOKEN_TYPE, payload);
            Instant expiresAt = Instant.ofEpochSecond(expiration);
            if (!expectedType.equals(type)) {
                throw invalidToken();
            }
            if (!expiresAt.isAfter(clock.instant())) {
                if ("refresh".equals(expectedType)) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "EXPIRED_REFRESH_TOKEN",
                            "Refresh Token이 만료되었습니다.");
                }
                throw invalidToken();
            }
            return new TokenClaims(memberId, type, expiresAt);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long refreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    private IssuedToken issue(long memberId, String type, long ttlSeconds) {
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        long expiresEpochSecond = clock.instant().plusSeconds(ttlSeconds).getEpochSecond();
        String payload = "{\"sub\":" + memberId + ",\"typ\":\"" + type
                + "\",\"exp\":" + expiresEpochSecond + ",\"jti\":\""
                + java.util.HexFormat.of().formatHex(nonce) + "\"}";
        String unsigned = ENCODER.encodeToString(HEADER.getBytes(StandardCharsets.UTF_8)) + "."
                + ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String token = unsigned + "." + ENCODER.encodeToString(sign(unsigned));
        return new IssuedToken(token, Instant.ofEpochSecond(expiresEpochSecond));
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private long captureLong(Pattern pattern, String payload) {
        return Long.parseLong(capture(pattern, payload));
    }

    private String capture(Pattern pattern, String payload) {
        Matcher matcher = pattern.matcher(payload);
        if (!matcher.find()) {
            throw invalidToken();
        }
        return matcher.group(1);
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않거나 만료된 토큰입니다.");
    }

    public record IssuedToken(String value, Instant expiresAt) { }

    public record TokenClaims(long memberId, String type, Instant expiresAt) { }
}
