package com.iriro.server.auth.presentation;

import com.iriro.server.auth.domain.RefreshSession;
import com.iriro.server.auth.infrastructure.EmailCredentialRepository;
import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.auth.infrastructure.RefreshSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final String PASSWORD = "my-password";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EmailCredentialRepository emailCredentialRepository;

    @Autowired
    RefreshSessionRepository refreshSessionRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @Test
    void signsUpWithNicknameAndLogsInWithIssuedTokens() throws Exception {
        String email = uniqueEmail();
        String nickname = uniqueNickname();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, nickname)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.errors").doesNotExist());

        assertThat(emailCredentialRepository.findByEmailNormalized(email).orElseThrow().getPasswordHash())
                .startsWith("$2a$");

        MvcResult login = mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        String refreshToken = extractToken(login, "refreshToken");
        assertThat(jwtTokenService.verify(refreshToken, "refresh").memberId()).isPositive();
        assertThat(refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(
                jwtTokenService.hash(refreshToken))).isPresent();
    }

    @Test
    void reissuesTokensAndLogoutRevokesTheRefreshSession() throws Exception {
        String email = uniqueEmail();
        signup(email, uniqueNickname());
        String firstRefresh = extractToken(login(email), "refreshToken");

        MvcResult reissue = mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String currentRefresh = extractToken(reissue, "refreshToken");
        assertThat(currentRefresh).isNotEqualTo(firstRefresh);
        assertThat(refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(
                jwtTokenService.hash(firstRefresh))).isEmpty();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(currentRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        assertThat(refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(
                jwtTokenService.hash(currentRefresh))).isEmpty();
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(currentRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rejectsInvalidRefreshTokenForReissueAndLogout() throws Exception {
        String request = refreshJson("not-a-token");
        mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void rejectsDuplicateEmailAndNickname() throws Exception {
        String email = uniqueEmail();
        String nickname = uniqueNickname();
        signup(email, nickname);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, uniqueNickname())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(uniqueEmail(), nickname.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    @Test
    void returnsSpecifiedErrorShapeForInvalidSignup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"","nickname":"x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void signup(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, nickname)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String signupJson(String email, String nickname) {
        return """
                {"email":"%s","password":"%s","nickname":"%s"}
                """.formatted(email, PASSWORD, nickname);
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

    private String extractToken(MvcResult result, String field) throws Exception {
        String response = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\":\\\"([^\\\"]+)\\\"").matcher(response);
        if (!matcher.find()) {
            throw new AssertionError("Token field missing from response: " + field);
        }
        return matcher.group(1);
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private String uniqueNickname() {
        return "n" + UUID.randomUUID().toString().substring(0, 11);
    }
}
