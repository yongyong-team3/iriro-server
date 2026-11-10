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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @Test
    void listsOnlyMembersNotificationsAndUsesCursor() throws Exception {
        Member member = member();
        Member other = member();
        notificationRepository.saveAndFlush(new Notification(member, null, "INFO", "one", "body"));
        Notification second = notificationRepository.saveAndFlush(new Notification(member, null, "INFO", "two", "body"));
        notificationRepository.saveAndFlush(new Notification(other, null, "INFO", "private", "body"));

        mockMvc.perform(get("/api/v1/notifications?limit=1")
                        .header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_LIST_SUCCESS"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("two"))
                .andExpect(jsonPath("$.data.nextCursorId").value(second.getId()))
                .andExpect(jsonPath("$.data.hasNext").value(true));
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
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void readsAndUpdatesOnlyCurrentMembersSettings() throws Exception {
        Member member = member();
        Member other = member();

        mockMvc.perform(get("/api/v1/notification-settings").header("Authorization", bearer(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryForecastEnabled").value(false));
        mockMvc.perform(patch("/api/v1/notification-settings")
                        .header("Authorization", bearer(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryForecastEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryForecastEnabled").value(true));
        mockMvc.perform(get("/api/v1/notification-settings").header("Authorization", bearer(other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryForecastEnabled").value(false));
    }

    @Test
    void registersAndReregistersDeviceAndPreventsCrossMemberClaim() throws Exception {
        Member member = member();
        Member other = member();
        String installationId = UUID.randomUUID().toString();
        String firstRequest = "{\"installationId\":\"" + installationId + "\",\"platform\":\"IOS\",\"expoPushToken\":\"ExponentPushToken[first]\"}";

        mockMvc.perform(post("/api/v1/devices").header("Authorization", bearer(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(firstRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.installationId").value(installationId))
                .andExpect(jsonPath("$.data.platform").value("IOS"));
        mockMvc.perform(post("/api/v1/devices").header("Authorization", bearer(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installationId\":\"" + installationId + "\",\"platform\":\"ANDROID\",\"expoPushToken\":\"ExponentPushToken[second]\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform").value("ANDROID"));
        Device registered = deviceRepository.findByInstallationId(installationId).orElseThrow();
        assertThat(registered.getExpoPushToken()).isEqualTo("ExponentPushToken[second]");

        mockMvc.perform(post("/api/v1/devices").header("Authorization", bearer(other.getId()))
                        .contentType(MediaType.APPLICATION_JSON).content(firstRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void unregistersOnlyOwnedDeviceAndReturnsNoContent() throws Exception {
        Member member = member();
        Member other = member();
        Device device = deviceRepository.saveAndFlush(new Device(member, UUID.randomUUID().toString(), DevicePlatform.IOS, "token"));
        Device foreign = deviceRepository.saveAndFlush(new Device(other, UUID.randomUUID().toString(), DevicePlatform.IOS, "token"));

        mockMvc.perform(delete("/api/v1/devices/{id}", device.getId()).header("Authorization", bearer(member.getId())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        assertThat(deviceRepository.findById(device.getId())).isEmpty();
        mockMvc.perform(delete("/api/v1/devices/{id}", foreign.getId()).header("Authorization", bearer(member.getId())))
                .andExpect(status().isNotFound());
        assertThat(deviceRepository.findById(foreign.getId())).isPresent();
    }

    @Test
    void rejectsMissingAndInvalidAccessTokens() throws Exception {
        Member member = member();
        String accessToken = jwtTokenService.issueAccessToken(member.getId()).value();
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errors").isArray());
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + accessToken + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rejectsInvalidNotificationQueryAndPathValuesWithBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/notifications?limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray());
        mockMvc.perform(get("/api/v1/notifications?limit=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
        mockMvc.perform(get("/api/v1/notifications?beforeId=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
        mockMvc.perform(get("/api/v1/notifications?limit=invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    private Member member() {
        return memberRepository.saveAndFlush(new Member(null, null));
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtTokenService.issueAccessToken(memberId).value();
    }
}
