package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.application.CurrentMemberProvider;
import com.iriro.server.common.application.ApiException;
import com.iriro.server.member.infrastructure.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAccessTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_TOKEN_BODY =
            "{\"code\":\"INVALID_TOKEN\",\"message\":\"인증 토큰이 유효하지 않습니다.\",\"errors\":[]}";

    private final JwtTokenService jwtTokenService;
    private final MemberRepository memberRepository;

    public JwtAccessTokenFilter(JwtTokenService jwtTokenService, MemberRepository memberRepository) {
        this.jwtTokenService = jwtTokenService;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            reject(response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        JwtTokenService.TokenClaims claims;
        try {
            claims = jwtTokenService.verify(token, "access");
        } catch (ApiException exception) {
            reject(response);
            return;
        }
        if (!memberRepository.existsById(claims.memberId())) {
            reject(response);
            return;
        }

        request.setAttribute(CurrentMemberProvider.MEMBER_ID_ATTRIBUTE, claims.memberId());
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(INVALID_TOKEN_BODY);
    }
}
