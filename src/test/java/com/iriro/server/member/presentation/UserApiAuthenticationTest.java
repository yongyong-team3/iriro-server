package com.iriro.server.member.presentation;

import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.activity.infrastructure.ActivityRepository;
import com.iriro.server.badge.infrastructure.BadgeAwardRepository;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:user-api-auth;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Transactional
class UserApiAuthenticationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenService jwtTokenService;
    @Autowired MemberRepository memberRepository;
    @MockitoBean ActivityRepository activityRepository;
    @MockitoBean BadgeAwardRepository badgeAwardRepository;

    @Test
    void usesAuthenticatedMemberInsteadOfQueryMemberId() throws Exception {
        Member first = memberRepository.saveAndFlush(new Member("First", "first"));
        Member second = memberRepository.saveAndFlush(new Member("Second", "second"));
        String token = jwtTokenService.issueAccessToken(first.getId()).value();

        mockMvc.perform(get("/api/v1/users/me").param("memberId", second.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(first.getId()));
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
