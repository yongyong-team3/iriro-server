package com.iriro.server.member.presentation;

import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:user-nickname-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1")
class UserNicknameConcurrencyTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenService jwtTokenService;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean MemberRepository memberRepository;

    @Test
    void simultaneousNicknameChangesLeaveOneOwnerAndReturnConflict() throws Exception {
        String firstName = uniqueName();
        String secondName = uniqueName();
        Member first = memberRepository.saveAndFlush(new Member(firstName, firstName));
        Member second = memberRepository.saveAndFlush(new Member(secondName, secondName));
        String target = uniqueName();
        CountDownLatch bothChecked = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothChecked.countDown();
            assertThat(bothChecked.await(5, TimeUnit.SECONDS)).isTrue();
            return false;
        }).when(memberRepository).existsByMemberNameKeyAndIdNot(eq(target), anyLong());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<MvcResult> firstRequest = request(first.getId(), target, ready, start);
            Callable<MvcResult> secondRequest = request(second.getId(), target, ready, start);
            var firstResult = executor.submit(firstRequest);
            var secondResult = executor.submit(secondRequest);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MvcResult> results = List.of(firstResult.get(15, TimeUnit.SECONDS),
                    secondResult.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()).sorted().toArray())
                    .containsExactly(200, 409);
            assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst().orElseThrow().getResponse().getContentAsString())
                    .contains("DUPLICATE_MEMBER_NAME");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members WHERE member_name_key = ?",
                Long.class, target)).isEqualTo(1);
    }

    @Test
    void updateAndAvailabilityUseTheSameNicknameRules() throws Exception {
        String oldName = uniqueName();
        Member member = memberRepository.saveAndFlush(new Member(oldName, oldName));
        String authorization = "Bearer " + jwtTokenService.issueAccessToken(member.getId()).value();

        mockMvc.perform(get("/api/v1/users/nickname-availability").param("memberName", "ab_"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memberName\":\"ab_\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/users/nickname-availability").param("memberName", "가2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memberName\":\"가2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberName").value("가2"));
        mockMvc.perform(get("/api/v1/users/nickname-availability").param("memberName", "가2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    private Callable<MvcResult> request(Long memberId, String target, CountDownLatch ready,
                                        CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return mockMvc.perform(patch("/api/v1/users/me")
                            .header("Authorization", "Bearer " + jwtTokenService.issueAccessToken(memberId).value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberName\":\"" + target + "\"}"))
                    .andReturn();
        };
    }

    private String uniqueName() {
        return "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
