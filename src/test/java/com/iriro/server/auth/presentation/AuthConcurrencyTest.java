package com.iriro.server.auth.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iriro.server.auth.infrastructure.KakaoSocialProviderClient;
import com.iriro.server.auth.infrastructure.NaverSocialProviderClient;
import com.iriro.server.auth.infrastructure.SocialProviderClient.SocialUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:auth-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1")
class AuthConcurrencyTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean KakaoSocialProviderClient kakaoClient;
    @MockitoBean NaverSocialProviderClient naverClient;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void concurrentSignupCreatesOneAccount() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = """
                {"email":"%s","password":"Password123","memberName":"n%s"}
                """.formatted(email, UUID.randomUUID().toString().substring(0, 10));
        List<MvcResult> results = race(() -> request("/signup", body));
        assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()).sorted().toArray())
                .containsExactly(201, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_credentials WHERE email_normalized = ?",
                Long.class, email)).isEqualTo(1);
    }

    @Test
    void concurrentFirstSocialLoginCreatesOneMember() throws Exception {
        String providerId = UUID.randomUUID().toString();
        when(kakaoClient.verifyAndGetUser("same-token")).thenReturn(new SocialUserInfo(providerId));
        List<MvcResult> results = race(() -> request("/login",
                """
                {"provider":"KAKAO","providerToken":"same-token"}
                """));
        assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()))
                .contains(200);
        assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()))
                .allMatch(status -> status == 200 || status == 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_accounts WHERE provider_user_id = ?",
                Long.class, providerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members m JOIN social_accounts s ON m.id=s.member_id WHERE s.provider_user_id = ?",
                Long.class, providerId)).isEqualTo(1);
    }

    @Test
    void concurrentReissueAllowsOnlyOneRotation() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        request("/signup", """
                {"email":"%s","password":"Password123","memberName":"n%s"}
                """.formatted(email, UUID.randomUUID().toString().substring(0, 10)));
        JsonNode login = mapper.readTree(request("/email/login",
                """
                {"email":"%s","password":"Password123"}
                """.formatted(email)).getResponse().getContentAsString());
        String refresh = login.path("data").path("refreshToken").asText();
        List<MvcResult> results = race(() -> request("/reissue",
                """
                {"refreshToken":"%s"}
                """.formatted(refresh)));
        assertThat(results.stream().mapToInt(result -> result.getResponse().getStatus()).sorted().toArray())
                .containsExactly(200, 401);
    }

    private MvcResult request(String path, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth" + path)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private List<MvcResult> race(Callable<MvcResult> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<MvcResult> simultaneous = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return action.call();
            };
            Future<MvcResult> first = executor.submit(simultaneous);
            Future<MvcResult> second = executor.submit(simultaneous);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }
}
