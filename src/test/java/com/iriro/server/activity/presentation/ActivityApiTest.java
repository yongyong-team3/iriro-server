package com.iriro.server.activity.presentation;

import com.iriro.server.activity.application.ActivityService;
import com.iriro.server.auth.infrastructure.JwtTokenService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:activity-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
class ActivityApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository members;
    @Autowired JwtTokenService jwt;
    @MockitoBean ActivityService service;

    @Test
    void returnsCreatedShape() throws Exception {
        String token = accessToken();
        given(service.save(anyLong(), anyString(), any(ActivitySaveRequest.class)))
                .willReturn(new ActivitySaveResponse(501L, true, false));
        mockMvc.perform(post("/api/v1/activities")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content(body("false")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.activityId").value(501))
                .andExpect(jsonPath("$.data.favoriteAdded").value(true))
                .andExpect(jsonPath("$.data.simulated").value(false));
    }

    @Test
    void rejectsExplicitNullAndStringSimulation() throws Exception {
        String token = accessToken();
        for (String simulated : new String[]{"null", "\"false\"", "0"}) {
            mockMvc.perform(post("/api/v1/activities")
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", UUID.randomUUID())
                            .contentType("application/json").content(body(simulated)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void requiresTokenAndIdempotencyHeader() throws Exception {
        mockMvc.perform(post("/api/v1/activities")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content(body("false")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/activities")
                        .header("Authorization", "Bearer " + accessToken())
                        .contentType("application/json").content(body("false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void openApiDocumentsActivityContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/activities'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/activities'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/activities'].post.parameters[0].name").value("Idempotency-Key"))
                .andExpect(jsonPath("$.paths['/api/v1/activities'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.schemas.BadgeElementResponse.properties.earned").exists());
    }

    private String accessToken() {
        String name = "Act" + UUID.randomUUID().toString().substring(0, 8);
        Member member = members.saveAndFlush(new Member(name, name.toLowerCase()));
        return jwt.issueAccessToken(member.getId()).value();
    }

    private String body(String simulated) {
        return """
                {"routeId":11,"startPointId":101,"activityType":"WALK",
                "startedAt":"2026-11-09T10:00:00+09:00","endedAt":"2026-11-09T10:30:00+09:00",
                "elapsedSeconds":1800,"distanceMeters":2400,
                "trackPoints":[{"latitude":37.3245,"longitude":127.2355,"recordedAt":"2026-11-09T10:00:00+09:00"},
                {"latitude":37.325,"longitude":127.236,"recordedAt":"2026-11-09T10:30:00+09:00"}],
                "simulated":%s}
                """.formatted(simulated);
    }
}
