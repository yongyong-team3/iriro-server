package com.iriro.server.auth.application;

import com.iriro.server.auth.domain.EmailCredential;
import com.iriro.server.auth.domain.RefreshSession;
import com.iriro.server.auth.infrastructure.EmailCredentialRepository;
import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.auth.infrastructure.RefreshSessionRepository;
import com.iriro.server.auth.presentation.AuthResponse;
import com.iriro.server.auth.presentation.AuthTokenResponse;
import com.iriro.server.auth.presentation.EmailLoginRequest;
import com.iriro.server.auth.presentation.EmailSignupRequest;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class EmailAuthService {

    private static final int BCRYPT_MAX_INPUT_BYTES = 72;

    private final MemberRepository memberRepository;
    private final EmailCredentialRepository emailCredentialRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TokenSessionService tokenSessionService;
    private final Clock clock = Clock.systemUTC();

    public EmailAuthService(
            MemberRepository memberRepository,
            EmailCredentialRepository emailCredentialRepository,
            RefreshSessionRepository refreshSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            TokenSessionService tokenSessionService
    ) {
        this.memberRepository = memberRepository;
        this.emailCredentialRepository = emailCredentialRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.tokenSessionService = tokenSessionService;
    }

    @Transactional
    public AuthResponse signup(EmailSignupRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        String nickname = normalizeNickname(request.nickname());
        validateBcryptInput(request.password());
        if (emailCredentialRepository.existsByEmailNormalized(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다.", "email");
        }
        String nicknameKey = nickname.toLowerCase(Locale.ROOT);
        if (memberRepository.existsByMemberNameKey(nicknameKey)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.", "nickname");
        }

        Member member;
        try {
            member = memberRepository.save(new Member(nickname, nicknameKey));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.", "nickname");
        }
        EmailCredential credential = new EmailCredential(
                member,
                normalizedEmail,
                passwordEncoder.encode(request.password())
        );
        try {
            emailCredentialRepository.saveAndFlush(credential);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다.", "email");
        }
        return new AuthResponse(member.getId(), normalizedEmail, nickname);
    }

    @Transactional
    public AuthTokenResponse login(EmailLoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        validateBcryptInput(request.password());
        EmailCredential credential = emailCredentialRepository.findByEmailNormalized(normalizedEmail)
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw invalidCredentials();
        }
        return tokenSessionService.issueTokens(credential.getMember());
    }

    @Transactional
    public AuthTokenResponse reissue(String refreshToken) {
        JwtTokenService.TokenClaims claims = jwtTokenService.verify(refreshToken, "refresh");
        RefreshSession oldSession = findActiveSession(refreshToken);
        if (!oldSession.getExpiresAt().isAfter(now())) {
            throw invalidToken();
        }
        if (oldSession.getMember().getId() != claims.memberId()) {
            throw invalidToken();
        }
        oldSession.revoke(now());
        return tokenSessionService.issueTokens(oldSession.getMember());
    }

    @Transactional
    public void logout(String refreshToken) {
        jwtTokenService.verify(refreshToken, "refresh");
        RefreshSession session = findActiveSession(refreshToken);
        if (!session.getExpiresAt().isAfter(now())) {
            throw invalidToken();
        }
        session.revoke(now());
    }

    private RefreshSession findActiveSession(String refreshToken) {
        return refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(jwtTokenService.hash(refreshToken))
                .orElseThrow(this::invalidToken);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNickname(String nickname) {
        String normalized = nickname.trim();
        int length = normalized.codePointCount(0, normalized.length());
        int keyLength = normalized.toLowerCase(Locale.ROOT).codePointCount(
                0, normalized.toLowerCase(Locale.ROOT).length()
        );
        if (length < 2 || length > 12 || keyLength > 12) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "닉네임은 2자 이상 12자 이하여야 합니다.", "nickname");
        }
        return normalized;
    }

    private void validateBcryptInput(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_INPUT_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "비밀번호 입력 길이를 확인해 주세요.", "password");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(clock), clock.getZone());
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않거나 만료된 토큰입니다.");
    }
}
