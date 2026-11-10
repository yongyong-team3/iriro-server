package com.iriro.server.auth.application;

import com.iriro.server.auth.domain.RefreshSession;
import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.auth.infrastructure.RefreshSessionRepository;
import com.iriro.server.auth.presentation.AuthTokenResponse;
import com.iriro.server.member.domain.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class TokenSessionService {

    private final JwtTokenService jwtTokenService;
    private final RefreshSessionRepository refreshSessionRepository;
    private final Clock clock = Clock.systemUTC();

    public TokenSessionService(
            JwtTokenService jwtTokenService,
            RefreshSessionRepository refreshSessionRepository
    ) {
        this.jwtTokenService = jwtTokenService;
        this.refreshSessionRepository = refreshSessionRepository;
    }

    @Transactional
    public AuthTokenResponse issueTokens(Member member) {
        JwtTokenService.IssuedToken access = jwtTokenService.issueAccessToken(member.getId());
        JwtTokenService.IssuedToken refresh = jwtTokenService.issueRefreshToken(member.getId());
        LocalDateTime expiresAt = LocalDateTime.ofInstant(refresh.expiresAt(), clock.getZone());
        refreshSessionRepository.saveAndFlush(new RefreshSession(
                member,
                jwtTokenService.hash(refresh.value()),
                expiresAt
        ));
        return new AuthTokenResponse("Bearer", access.value(), refresh.value(),
                jwtTokenService.accessTtlSeconds(), jwtTokenService.refreshTtlSeconds());
    }

    public long refreshTtlSeconds() {
        return jwtTokenService.refreshTtlSeconds();
    }
}
