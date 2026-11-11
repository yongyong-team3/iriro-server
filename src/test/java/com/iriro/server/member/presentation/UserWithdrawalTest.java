package com.iriro.server.member.presentation;

import com.iriro.server.auth.application.TokenSessionService;
import com.iriro.server.auth.infrastructure.RefreshSessionRepository;
import com.iriro.server.auth.presentation.AuthTokenResponse;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:user-withdrawal;MODE=MySQL;DB_CLOSE_DELAY=-1")
class UserWithdrawalTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired TokenSessionService tokenSessionService;
    @Autowired RefreshSessionRepository refreshSessionRepository;

    @Test
    void withdrawalDeletesAccountAndRejectsIssuedAccessToken() throws Exception {
        Member member = memberRepository.saveAndFlush(new Member("Withdraw", "withdraw"));
        AuthTokenResponse tokens = tokenSessionService.issueTokens(member);

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.existsById(member.getId())).isFalse();
        assertThat(refreshSessionRepository.findAll()).isEmpty();
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawalRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
