package com.iriro.server.auth.application;

import com.iriro.server.auth.domain.SocialAccount;
import com.iriro.server.auth.domain.SocialProvider;
import com.iriro.server.auth.infrastructure.SocialAccountRepository;
import com.iriro.server.auth.presentation.AuthTokenResponse;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialAccountLoginService {

    private final SocialAccountRepository socialAccountRepository;
    private final MemberRepository memberRepository;
    private final TokenSessionService tokenSessionService;

    public SocialAccountLoginService(
            SocialAccountRepository socialAccountRepository,
            MemberRepository memberRepository,
            TokenSessionService tokenSessionService
    ) {
        this.socialAccountRepository = socialAccountRepository;
        this.memberRepository = memberRepository;
        this.tokenSessionService = tokenSessionService;
    }

    @Transactional
    public AuthTokenResponse login(SocialProvider provider, String providerUserId) {
        SocialAccount socialAccount = socialAccountRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .orElseGet(() -> createSocialAccount(provider, providerUserId));
        return tokenSessionService.issueTokens(socialAccount.getMember());
    }

    private SocialAccount createSocialAccount(SocialProvider provider, String providerUserId) {
        // No email/nickname matching: a provider identity creates its own member unless already linked.
        Member member = memberRepository.save(new Member(null, null));
        return socialAccountRepository.saveAndFlush(new SocialAccount(member, provider, providerUserId));
    }
}
