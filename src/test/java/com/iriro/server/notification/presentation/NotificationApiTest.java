package com.iriro.server.notification.presentation;

import com.iriro.server.auth.infrastructure.JwtTokenService;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.notification.domain.Device;
import com.iriro.server.notification.domain.DevicePlatform;
import com.iriro.server.notification.domain.Notification;
import com.iriro.server.notification.infrastructure.DeviceRepository;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:notification-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Transactional
class NotificationApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired JwtTokenService jwtTokenService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void listsOnlyRecoveryNotificationsWithPagingAndUnreadFilter() throws Exception {
        Member member = member();
        Member other = member();
        notificationRepository.saveAndFlush(new Notification(member, null, "INFO", "one", "body"));
        long trailId = insertId("INSERT INTO trails(name) VALUES ('test')");
        long eventId = insertEvent(trailId);
        Notification second = notificationRepository.saveAndFlush(new Notification(member, eventId, "RECOVERY_FORECAST", "two", "body"));
        notificationRepository.saveAndFlush(new Notification(other, null, "INFO", "private", "body"));

        mockMvc.perform(get("/api/v1/notifications?size=1&page=0")
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("two"))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(second.getId()))
                .andExpect(jsonPath("$.data.content[0].trailId").value(trailId))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mockMvc.perform(get("/api/v1/notifications?unreadOnly=true")
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
        mockMvc.perform(get("/api/v1/notifications?size=100&page=1")
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", second.getId())
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications?unreadOnly=true")
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void marksOwnedNotificationReadIdempotentlyAndHidesOtherMembersNotification() throws Exception {
        Member member = member();
        Member other = member();
        Notification own = notificationRepository.saveAndFlush(new Notification(member, null, "INFO", "own", "body"));
        Notification foreign = notificationRepository.saveAndFlush(new Notification(other, null, "INFO", "foreign", "body"));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", own.getId())
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(own.getId()))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        var firstReadAt = notificationRepository.findById(own.getId()).orElseThrow().getReadAt();
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", own.getId())
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk());
        assertThat(notificationRepository.findById(own.getId()).orElseThrow().getReadAt()).isEqualTo(firstReadAt);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", foreign.getId())
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors").value((Object) null));
    }

    @Test
    void readsAndUpdatesOnlyCurrentMembersSettings() throws Exception {
        Member member = member();
        Member other = member();

        mockMvc.perform(get("/api/v1/notification-settings").header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("조회되었습니다."))
                .andExpect(jsonPath("$.data.recoveryForecastEnabled").value(false));
        mockMvc.perform(patch("/api/v1/notification-settings")
                        .header("Authorization", bearer(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryForecastEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("알림 설정이 변경되었습니다."))
                .andExpect(jsonPath("$.data.recoveryForecastEnabled").value(true))
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
        mockMvc.perform(get("/api/v1/notification-settings").header("Authorization", bearer(other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryForecastEnabled").value(false));
    }

    @Test
    void rejectsMissingOrInvalidNotificationSetting() throws Exception {
        Member member = member();
        for (String body : new String[]{"{}", "{\"recoveryForecastEnabled\":\"yes\"}"}) {
            mockMvc.perform(patch("/api/v1/notification-settings")
                            .header("Authorization", bearer(member.getId()))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void registersAndReregistersDeviceAndPreventsCrossMemberClaim() throws Exception {
        Member member = member();
        Member other = member();
        String installationId = UUID.randomUUID().toString();
        String firstRequest = "{\"installationId\":\"" + installationId + "\",\"platform\":\"IOS\",\"expoPushToken\":\"ExponentPushToken[first]\"}";

        mockMvc.perform(post("/api/v1/devices").header("Authorization", bearer(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(firstRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("기기가 등록되었습니다."))
                .andExpect(jsonPath("$.data.deviceId").isNumber())
                .andExpect(jsonPath("$.data.installationId").doesNotExist())
                .andExpect(jsonPath("$.data.platform").doesNotExist());
        mockMvc.perform(post("/api/v1/devices").header("Authorization", bearer(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installationId\":\"" + installationId + "\",\"platform\":\"ANDROID\",\"expoPushToken\":\"ExponentPushToken[second]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").isNumber());
        Device registered = deviceRepository.findByInstallationId(installationId).orElseThrow();
        assertThat(registered.getExpoPushToken()).isEqualTo("ExponentPushToken[second]");
        assertThat(deviceRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/devices").header("Authorization", bearer(other.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(firstRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors").value((Object) null));
    }

    @Test
    void unregistersOnlyOwnedDeviceAndReturnsNoContent() throws Exception {
        Member member = member();
        Member other = member();
        Device device = deviceRepository.saveAndFlush(new Device(member, UUID.randomUUID().toString(), DevicePlatform.IOS, "token"));
        Device remaining = deviceRepository.saveAndFlush(new Device(member, UUID.randomUUID().toString(), DevicePlatform.ANDROID, "remaining-token"));
        Device foreign = deviceRepository.saveAndFlush(new Device(other, UUID.randomUUID().toString(), DevicePlatform.IOS, "token"));

        mockMvc.perform(delete("/api/v1/devices/{id}", device.getId()).header("Authorization", bearer(member.getId())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getExpoPushToken()).isNull();
        mockMvc.perform(delete("/api/v1/devices/{id}", device.getId()).header("Authorization", bearer(member.getId())))
                .andExpect(status().isNoContent());
        assertThat(deviceRepository.findById(remaining.getId()).orElseThrow().getExpoPushToken())
                .isEqualTo("remaining-token");
        mockMvc.perform(delete("/api/v1/devices/{id}", foreign.getId()).header("Authorization", bearer(member.getId())))
                .andExpect(status().isNotFound());
        assertThat(deviceRepository.findById(foreign.getId())).isPresent();
        mockMvc.perform(post("/api/v1/devices").header("Authorization", bearer(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installationId\":\"" + device.getInstallationId()
                                + "\",\"platform\":\"IOS\",\"expoPushToken\":\"ExpoPushToken[renewed]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(device.getId()));
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getExpoPushToken())
                .isEqualTo("ExpoPushToken[renewed]");
    }

    @Test
    void rejectsInvalidDeviceRegistration() throws Exception {
        Member member = member();
        String uuid = UUID.randomUUID().toString();
        for (String body : new String[]{
                "{\"installationId\":\"bad\",\"platform\":\"IOS\",\"expoPushToken\":\"ExpoPushToken[x]\"}",
                "{\"installationId\":\"" + uuid + "\",\"platform\":\"OTHER\",\"expoPushToken\":\"ExpoPushToken[x]\"}",
                "{\"installationId\":\"" + uuid + "\",\"platform\":\"IOS\"}",
                "{\"installationId\":\"" + uuid + "\",\"platform\":\"IOS\",\"expoPushToken\":\"invalid\"}"}) {
            mockMvc.perform(post("/api/v1/devices").header("Authorization", bearer(member.getId()))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void rejectsMissingAndInvalidAccessTokens() throws Exception {
        Member member = member();
        String accessToken = jwtTokenService.issueAccessToken(member.getId()).value();
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errors").value((Object) null));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + accessToken + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors").value((Object) null));
    }

    @Test
    void rejectsInvalidNotificationQueryAndPathValuesWithBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/notifications?size=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").value((Object) null));
        mockMvc.perform(get("/api/v1/notifications?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").value((Object) null));
        mockMvc.perform(get("/api/v1/notifications?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").value((Object) null));
        mockMvc.perform(get("/api/v1/notifications?size=invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").value((Object) null));
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").value((Object) null));
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").value((Object) null));
    }

    private Member member() {
        return memberRepository.saveAndFlush(new Member(null, null));
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenService.issueAccessToken(memberId).value();
    }

    private long insertEvent(long trailId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO recovery_events(trail_id, started_at) VALUES (?, CURRENT_TIMESTAMP)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, trailId);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private long insertId(String sql) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS), keys);
        return keys.getKey().longValue();
    }
}
