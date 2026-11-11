package com.iriro.server.badge.presentation;

import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.badge.application.BadgeService;
import com.iriro.server.badge.domain.BadgeCode;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:badge-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
class BadgeApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenService jwtTokenService;
    @Autowired MemberRepository memberRepository;
    @MockitoBean BadgeService badgeService;

    @Test
    void authenticatedBadgeResponseIncludesKoreanOffset() throws Exception {
        Member member = memberRepository.saveAndFlush(new Member("Tester", "tester"));
        given(badgeService.getMyBadges(member.getId())).willReturn(BadgeListResponse.of(1, List.of(
                BadgeElementResponse.of(BadgeCode.WALK_LOVER, 20, LocalDateTime.of(2026, 11, 11, 10, 30)),
                BadgeElementResponse.of(BadgeCode.BIKE_RIDER, 2400, null)
        )));

        mockMvc.perform(get("/api/v1/badges")
                        .header("Authorization", "Bearer " + jwtTokenService.issueAccessToken(member.getId()).value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.badges[0].earnedAt").value("2026-11-11T10:30:00+09:00"))
                .andExpect(jsonPath("$.data.badges[0].earned").value(true))
                .andExpect(jsonPath("$.data.badges[1].earnedAt").value((Object) null));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/badges"))
                .andExpect(status().isUnauthorized());
    }
}
