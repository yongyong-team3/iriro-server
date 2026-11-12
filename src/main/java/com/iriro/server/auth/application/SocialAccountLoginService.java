package com.iriro.server.auth.application;

import com.iriro.server.auth.domain.SocialAccount;
import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.auth.infrastructure.SocialAccountRepository;
import com.iriro.server.auth.presentation.AuthTokenResponse;
import com.iriro.server.auth.presentation.SocialLoginResponse;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.member.application.ProfilePhotoService;
import com.iriro.server.common.application.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialAccountLoginService {

    private final SocialAccountRepository socialAccountRepository;
    private final MemberRepository memberRepository;
    private final TokenSessionService tokenSessionService;
    private final ProfilePhotoService profilePhotoService;

    public SocialAccountLoginService(
            SocialAccountRepository socialAccountRepository,
            MemberRepository memberRepository,
            TokenSessionService tokenSessionService,
            ProfilePhotoService profilePhotoService
    ) {
        this.socialAccountRepository = socialAccountRepository;
        this.memberRepository = memberRepository;
        this.tokenSessionService = tokenSessionService;
        this.profilePhotoService = profilePhotoService;
    }

    @Transactional
    public SocialLoginResponse login(SocialProvider provider, String providerUserId) {
        var existing = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        SocialAccount socialAccount = existing.orElseGet(() -> createSocialAccount(provider, providerUserId));
        Member member = socialAccount.getMember();
        AuthTokenResponse tokens = tokenSessionService.issueTokens(member);
        return new SocialLoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(),
                tokens.accessTokenExpiresInSeconds(), tokens.refreshTokenExpiresInSeconds(), existing.isEmpty(),
                member.getMemberName() == null,
                new SocialLoginResponse.MemberInfo(member.getId(), member.getMemberName(),
                        profilePhotoService.urlIfPresent(member.getId())));
    }

    private SocialAccount createSocialAccount(SocialProvider provider, String providerUserId) {
        // No email/nickname matching: a provider identity creates its own member unless already linked.
        Member member = memberRepository.save(new Member(null, null));
        try {
            return socialAccountRepository.saveAndFlush(new SocialAccount(member, provider, providerUserId));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SOCIAL_ACCOUNT_CONFLICT", "소셜 계정 처리 중 충돌이 발생했습니다.");
        }
    }
}
