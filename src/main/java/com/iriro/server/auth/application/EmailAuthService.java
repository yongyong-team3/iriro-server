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
import com.iriro.server.auth.presentation.SocialLoginResponse;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.domain.NicknamePolicy;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.member.application.ProfilePhotoService;
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
    private final ProfilePhotoService profilePhotoService;
    private final Clock clock = Clock.systemUTC();

    public EmailAuthService(
            MemberRepository memberRepository,
            EmailCredentialRepository emailCredentialRepository,
            RefreshSessionRepository refreshSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            TokenSessionService tokenSessionService,
            ProfilePhotoService profilePhotoService
    ) {
        this.memberRepository = memberRepository;
        this.emailCredentialRepository = emailCredentialRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.tokenSessionService = tokenSessionService;
        this.profilePhotoService = profilePhotoService;
    }

    @Transactional
    public AuthResponse signup(EmailSignupRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        String nickname = normalizeNickname(request.memberName());
        validateBcryptInput(request.password());
        if (emailCredentialRepository.existsByEmailNormalized(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입한 이메일입니다.", "email");
        }
        String nicknameKey = nickname.toLowerCase(Locale.ROOT);
        if (memberRepository.existsByMemberNameKey(nicknameKey)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_MEMBER_NAME", "이미 사용 중인 닉네임입니다.", "memberName");
        }

        Member member;
        try {
            member = memberRepository.save(new Member(nickname, nicknameKey));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_MEMBER_NAME", "이미 사용 중인 닉네임입니다.", "memberName");
        }
        EmailCredential credential = new EmailCredential(
                member,
                normalizedEmail,
                passwordEncoder.encode(request.password())
        );
        try {
            emailCredentialRepository.saveAndFlush(credential);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입한 이메일입니다.", "email");
        }
        return new AuthResponse(member.getId());
    }

    @Transactional
    public SocialLoginResponse login(EmailLoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        validateBcryptInput(request.password());
        EmailCredential credential = emailCredentialRepository.findByEmailNormalized(normalizedEmail)
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw invalidCredentials();
        }
        Member member = credential.getMember();
        AuthTokenResponse tokens = tokenSessionService.issueTokens(member);
        return new SocialLoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(),
                tokens.accessTokenExpiresInSeconds(), tokens.refreshTokenExpiresInSeconds(),
                false, false, new SocialLoginResponse.MemberInfo(member.getId(), member.getMemberName(),
                        profilePhotoService.urlIfPresent(member.getId())));
    }

    @Transactional
    public AuthTokenResponse reissue(String refreshToken) {
        JwtTokenService.TokenClaims claims;
        try {
            claims = jwtTokenService.verify(refreshToken, "refresh");
        } catch (ApiException exception) {
            if ("EXPIRED_REFRESH_TOKEN".equals(exception.getCode())) {
                throw exception;
            }
            throw invalidToken();
        }
        RefreshSession oldSession = findActiveSession(refreshToken);
        if (!oldSession.getExpiresAt().isAfter(now())) {
            throw expiredToken();
        }
        if (oldSession.getMember().getId() != claims.memberId()) {
            throw invalidToken();
        }
        oldSession.revoke(now());
        return tokenSessionService.issueTokens(oldSession.getMember());
    }

    @Transactional
    public void logout(String refreshToken) {
        try {
            jwtTokenService.verify(refreshToken, "refresh");
        } catch (ApiException ignored) {
            return;
        }
        refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(jwtTokenService.hash(refreshToken))
                .ifPresent(session -> session.revoke(now()));
    }

    private RefreshSession findActiveSession(String refreshToken) {
        return refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(jwtTokenService.hash(refreshToken))
                .orElseThrow(this::invalidToken);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNickname(String nickname) {
        if (!NicknamePolicy.isValid(nickname)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.", "memberName");
        }
        return nickname;
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
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh Token이 유효하지 않습니다.");
    }

    private ApiException expiredToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "EXPIRED_REFRESH_TOKEN", "Refresh Token이 만료되었습니다.");
    }
}
