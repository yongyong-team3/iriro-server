package com.iriro.server.auth.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iriro.server.auth.infrastructure.EmailCredentialRepository;
import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.auth.infrastructure.RefreshSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:auth-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Transactional
class AuthApiTest {

    private static final String PASSWORD = "Password123";

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired EmailCredentialRepository emailCredentials;
    @Autowired RefreshSessionRepository sessions;
    @Autowired JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void signupReturnsOnlyMemberIdAndStoresNormalizedEmailAndHash() throws Exception {
        String email = uniqueEmail();
        String nickname = uniqueNickname();
        JsonNode result = json(mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("  " + email.toUpperCase() + "  ", PASSWORD, nickname)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andReturn());

        assertThat(result.path("data").size()).isEqualTo(1);
        assertThat(result.path("data").path("memberId").asLong()).isPositive();
        var credential = emailCredentials.findByEmailNormalized(email).orElseThrow();
        assertThat(credential.getPasswordHash()).startsWith("$2a$").isNotEqualTo(PASSWORD);
        assertThat(credential.getMemberId()).isEqualTo(result.path("data").path("memberId").asLong());
    }

    @Test
    void duplicateEmailAndNicknameUseSpecifiedConflictCodes() throws Exception {
        String email = uniqueEmail();
        String nickname = uniqueNickname();
        signup(email, PASSWORD, nickname);
        expectError(signupJson(" " + email.toUpperCase() + " ", PASSWORD, uniqueNickname()),
                "/signup", 409, "DUPLICATE_EMAIL");
        expectError(signupJson(uniqueEmail(), PASSWORD, nickname),
                "/signup", 409, "DUPLICATE_MEMBER_NAME");
    }

    @Test
    void signupRejectsMissingMalformedAndOutOfRangeFields() throws Exception {
        String email = uniqueEmail();
        for (String body : new String[] {
                "{}", signupJson("not-an-email", PASSWORD, uniqueNickname()),
                signupJson(email, "password", uniqueNickname()),
                signupJson(email, "12345678", uniqueNickname()),
                signupJson(email, "Abc1234", uniqueNickname()),
                signupJson(email, "A".repeat(64) + "1", uniqueNickname()),
                signupJson(email, PASSWORD, "x"),
                signupJson(email, PASSWORD, "x".repeat(13)),
                signupJson(email, PASSWORD, "bad name"),
                signupJson(email, PASSWORD, "ab_"),
                signupJson(email, PASSWORD, "가나🙂")
        }) {
            expectError(body, "/signup", 400, "INVALID_REQUEST");
        }
    }

    @Test
    void signupAcceptsPasswordAndNicknameBoundaries() throws Exception {
        signup(uniqueEmail(), "Abcdefg1", "ab");
        signup(uniqueEmail(), "A".repeat(63) + "1", "a".repeat(12));
    }

    @Test
    void loginReturnsSpecifiedTokenAndMemberShapeAndNormalizesEmail() throws Exception {
        String email = uniqueEmail();
        String nickname = uniqueNickname();
        JsonNode signup = signup(email, PASSWORD, nickname);
        JsonNode data = login(" " + email.toUpperCase() + " ", PASSWORD);
        assertThat(data.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(data.path("accessTokenExpiresInSeconds").asLong()).isEqualTo(1800);
        assertThat(data.path("refreshTokenExpiresInSeconds").asLong()).isEqualTo(2592000);
        assertThat(data.path("isNewMember").asBoolean()).isFalse();
        assertThat(data.path("requiresNicknameSetup").asBoolean()).isFalse();
        assertThat(data.path("member").path("memberId").asLong())
                .isEqualTo(signup.path("data").path("memberId").asLong());
        assertThat(data.path("member").path("memberName").asText()).isEqualTo(nickname);
        assertThat(data.path("member").path("profileImageUrl").isNull()).isTrue();
        assertThat(tokens.verify(data.path("accessToken").asText(), "access").memberId())
                .isEqualTo(signup.path("data").path("memberId").asLong());
    }

    @Test
    void loginFailuresAreIndistinguishableAndIssueNoSession() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, uniqueNickname());
        long before = sessions.count();
        expectError(loginJson(uniqueEmail(), PASSWORD), "/email/login", 401, "INVALID_CREDENTIALS");
        expectError(loginJson(email, "Wrong123"), "/email/login", 401, "INVALID_CREDENTIALS");
        assertThat(sessions.count()).isEqualTo(before);
        for (String body : new String[] {"{}", loginJson("invalid", PASSWORD), loginJson(email, "")}) {
            expectError(body, "/email/login", 400, "INVALID_REQUEST");
        }
    }

    @Test
    void reissueRotatesRefreshTokenAndRejectsReusedAndForgedTokens() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, uniqueNickname());
        String first = login(email, PASSWORD).path("refreshToken").asText();
        String otherDevice = login(email, PASSWORD).path("refreshToken").asText();
        JsonNode replacement = json(mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("토큰이 재발급되었습니다."))
                .andReturn()).path("data");
        String second = replacement.path("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);
        assertThat(replacement.path("accessToken").asText()).isNotBlank();
        assertThat(replacement.path("accessTokenExpiresInSeconds").asLong()).isEqualTo(1800);
        assertThat(replacement.path("refreshTokenExpiresInSeconds").asLong()).isEqualTo(2592000);
        expectError(refreshJson(first), "/reissue", 401, "INVALID_REFRESH_TOKEN");
        expectError(refreshJson("forged"), "/reissue", 401, "INVALID_REFRESH_TOKEN");
        assertThat(sessions.findByTokenHashAndRevokedAtIsNull(tokens.hash(second))).isPresent();
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(otherDevice)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredAndMissingRefreshTokenAreRejected() throws Exception {
        JwtTokenService past = new JwtTokenService(
                "test-only-signing-key-not-for-any-real-environment-123456",
                1800, 1, Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
        expectError(refreshJson(past.issueRefreshToken(1).value()), "/reissue", 401, "EXPIRED_REFRESH_TOKEN");
        expectError("{}", "/reissue", 400, "INVALID_REQUEST");
    }

    @Test
    void logoutIsIdempotentWithoutAccessTokenAndDoesNotRevokeOtherDevice() throws Exception {
        String email = uniqueEmail();
        signup(email, PASSWORD, uniqueNickname());
        String first = login(email, PASSWORD).path("refreshToken").asText();
        String other = login(email, PASSWORD).path("refreshToken").asText();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON).content(refreshJson(first)))
                    .andExpect(status().isNoContent())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
        }
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson("invalid-token")))
                .andExpect(status().isNoContent());
        expectError(refreshJson(first), "/reissue", 401, "INVALID_REFRESH_TOKEN");
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshJson(other)))
                .andExpect(status().isOk());
        expectError("{}", "/logout", 400, "INVALID_REQUEST");
    }

    @Test
    void deletedMemberCanSignUpAgainWithSameEmailAndGetsNewId() throws Exception {
        String email = uniqueEmail();
        long originalId = signup(email, PASSWORD, uniqueNickname()).path("data").path("memberId").asLong();
        String oldRefresh = login(email, PASSWORD).path("refreshToken").asText();
        entityManager.flush();
        entityManager.clear();
        jdbc.update("DELETE FROM members WHERE id = ?", originalId);
        expectError(refreshJson(oldRefresh), "/reissue", 401, "INVALID_REFRESH_TOKEN");
        long replacementId = signup(email, PASSWORD, uniqueNickname()).path("data").path("memberId").asLong();
        assertThat(replacementId).isNotEqualTo(originalId);
        assertThat(login(email, PASSWORD).path("member").path("memberId").asLong()).isEqualTo(replacementId);
    }

    private JsonNode signup(String email, String password, String nickname) throws Exception {
        return json(mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(signupJson(email, password, nickname)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode login(String email, String password) throws Exception {
        return json(mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인되었습니다."))
                .andReturn()).path("data");
    }

    private void expectError(String body, String path, int statusCode, String code) throws Exception {
        JsonNode response = json(mockMvc.perform(post("/api/v1/auth" + path)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andReturn());
        assertThat(response.path("message").asText()).isNotBlank();
        assertThat(response.has("data")).isFalse();
        assertThat(response.has("errors")).isTrue();
        assertThat(response.path("errors").isNull()).isTrue();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String signupJson(String email, String password, String nickname) {
        return """
                {"email":"%s","password":"%s","memberName":"%s"}
                """.formatted(email, password, nickname);
    }

    private String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String refreshJson(String token) {
        return """
                {"refreshToken":"%s"}
                """.formatted(token);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private String uniqueNickname() {
        return "n" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }
}
