package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.application.SocialAccountLoginService.PreparedLogin;
import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.common.application.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Repository
public class OAuthFlowStore {
    private static final int STATE_TTL_SECONDS = 300;
    private static final int LOGIN_CODE_TTL_SECONDS = 60;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public OAuthFlowStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public String createState(SocialProvider provider, String redirectUri) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("DELETE FROM oauth_states WHERE expires_at < ?", now);
        String state = randomCode();
        jdbc.update("INSERT INTO oauth_states (state_hash, provider, redirect_uri, expires_at) VALUES (?, ?, ?, ?)",
                hash(state), provider.name(), redirectUri, now.plusSeconds(STATE_TTL_SECONDS));
        return state;
    }

    @Transactional
    public String consumeState(SocialProvider provider, String state) {
        if (state == null || state.isBlank()) throw invalidState();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String stateHash = hash(state);
        int updated = jdbc.update("""
                UPDATE oauth_states SET consumed_at = ?
                WHERE state_hash = ? AND provider = ? AND consumed_at IS NULL AND expires_at > ?
                """, now, stateHash, provider.name(), now);
        if (updated != 1) throw invalidState();
        return jdbc.queryForObject("SELECT redirect_uri FROM oauth_states WHERE state_hash = ?",
                String.class, stateHash);
    }

    @Transactional
    public String createLoginCode(PreparedLogin prepared) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("DELETE FROM oauth_login_codes WHERE expires_at < ?", now);
        String code = randomCode();
        jdbc.update("""
                INSERT INTO oauth_login_codes (code_hash, member_id, is_new_member, expires_at)
                VALUES (?, ?, ?, ?)
                """, hash(code), prepared.memberId(), prepared.isNewMember(),
                now.plusSeconds(LOGIN_CODE_TTL_SECONDS));
        return code;
    }

    @Transactional
    public PreparedLogin consumeLoginCode(String code) {
        if (code == null || code.isBlank()) throw invalidLoginCode();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String codeHash = hash(code);
        int updated = jdbc.update("""
                UPDATE oauth_login_codes SET consumed_at = ?
                WHERE code_hash = ? AND consumed_at IS NULL AND expires_at > ?
                """, now, codeHash, now);
        if (updated != 1) throw invalidLoginCode();
        return jdbc.queryForObject("SELECT member_id, is_new_member FROM oauth_login_codes WHERE code_hash = ?",
                (result, row) -> new PreparedLogin(result.getLong("member_id"),
                        result.getBoolean("is_new_member")), codeHash);
    }

    private String randomCode() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ApiException invalidState() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OAUTH_STATE", "로그인 요청 상태를 확인해 주세요.");
    }

    private ApiException invalidLoginCode() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN_CODE", "로그인 코드를 확인해 주세요.");
    }
}
