package com.iriro.server.notification.application;

import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.notification.domain.Device;
import com.iriro.server.notification.domain.DevicePlatform;
import com.iriro.server.notification.domain.NotificationSetting;
import com.iriro.server.notification.infrastructure.DeviceRepository;
import com.iriro.server.notification.infrastructure.ExpoPushGateway;
import com.iriro.server.notification.infrastructure.ExpoPushMessage;
import com.iriro.server.notification.infrastructure.ExpoPushReceipt;
import com.iriro.server.notification.infrastructure.ExpoPushTicket;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import com.iriro.server.notification.infrastructure.NotificationSettingRepository;
import com.iriro.server.notification.infrastructure.RecoveryEventRepository;
import com.iriro.server.notification.infrastructure.PendingPushReceiptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:recovery-push;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Transactional
class RecoveryNotificationServiceTest {
    @Autowired RecoveryNotificationService service;
    @Autowired MemberRepository memberRepository;
    @Autowired DeviceRepository deviceRepository;
    @Autowired NotificationSettingRepository settingRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired RecoveryEventRepository eventRepository;
    @Autowired PendingPushReceiptRepository pendingReceipts;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean ExpoPushGateway expoPushGateway;

    @Test
    void sendsToAllEnabledMembersOnceRegardlessOfFavoritesOrDevices() {
        Member eligible = setupRecipient(true, "ExponentPushToken[eligible]");
        setupRecipient(false, "ExponentPushToken[disabled]");
        Member noDevice = memberRepository.saveAndFlush(new Member(null, null));
        settingRepository.saveAndFlush(new NotificationSetting(noDevice, true));
        long eventId = createEvent();
        when(expoPushGateway.send(anyList())).thenReturn(List.of(new ExpoPushTicket("ok", "ticket-1", null, null)));

        RecoveryNotificationDispatchResult result = service.notifyRecoveryEvent(eventId, "산책로 회복", "이용할 수 있어요.");

        assertThat(result.notificationsCreated()).isEqualTo(2);
        assertThat(result.ticketDeviceIds()).hasSize(1);
        assertThat(notificationRepository.existsByEventIdAndMember_Id(eventId, eligible.getId())).isTrue();
        assertThat(notificationRepository.existsByEventIdAndMember_Id(eventId, noDevice.getId())).isTrue();
        assertThat(eventRepository.findById(eventId).orElseThrow().getFirstNotifiedAt()).isNotNull();
        verify(expoPushGateway).send(org.mockito.ArgumentMatchers.argThat(messages ->
                messages.size() == 1 && messages.getFirst().to().equals("ExponentPushToken[eligible]")));

        RecoveryNotificationDispatchResult duplicate = service.notifyRecoveryEvent(eventId, "산책로 회복", "이용할 수 있어요.");
        assertThat(duplicate.notificationsCreated()).isZero();
        Member late = setupRecipient(true, "ExponentPushToken[late]");
        RecoveryNotificationDispatchResult lateRetry = service.notifyRecoveryEvent(eventId, "산책로 회복", "이용할 수 있어요.");
        assertThat(lateRetry.notificationsCreated()).isZero();
        assertThat(notificationRepository.existsByEventIdAndMember_Id(eventId, late.getId())).isFalse();
        verify(expoPushGateway, times(1)).send(anyList());
    }

    @Test
    void deactivatesTokenOnTicketAndReceiptDeviceNotRegisteredErrors() {
        Member member = setupRecipient(true, "ExponentPushToken[bad]");
        Device device = deviceRepository.findAll().getFirst();
        long eventId = createEvent();
        when(expoPushGateway.send(anyList())).thenReturn(List.of(new ExpoPushTicket(
                "error", null, "Device is not registered", new ExpoPushTicket.ExpoErrorDetails("DeviceNotRegistered"))));

        RecoveryNotificationDispatchResult rejected = service.notifyRecoveryEvent(eventId, "title", "body");
        assertThat(rejected.ticketErrors()).hasSize(1);
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getExpoPushToken()).isNull();

        device.updateRegistration(DevicePlatform.IOS, "ExponentPushToken[again]");
        String ticketId = "ticket-receipt";
        when(expoPushGateway.send(anyList())).thenReturn(List.of(new ExpoPushTicket("ok", ticketId, null, null)));
        service.notifyRecoveryEvent(createEvent(), "title", "body");
        assertThat(pendingReceipts.count()).isEqualTo(1);
        when(expoPushGateway.getReceipts(List.of(ticketId))).thenReturn(Map.of(), Map.of(ticketId,
                new ExpoPushReceipt("error", "unregistered", new ExpoPushTicket.ExpoErrorDetails("DeviceNotRegistered"))));

        assertThat(service.checkPendingReceipts()).isEmpty();
        assertThat(pendingReceipts.count()).isEqualTo(1);

        Map<String, ExpoPushReceipt> receipts = service.checkPendingReceipts();

        assertThat(receipts).containsKey(ticketId);
        assertThat(deviceRepository.findById(device.getId()).orElseThrow().getExpoPushToken()).isNull();
        assertThat(pendingReceipts.count()).isZero();
    }

    @Test
    void receiptForOldTokenDoesNotDeactivateReplacementToken() {
        setupRecipient(true, "ExpoPushToken[old]");
        Device device = deviceRepository.findAll().getFirst();
        when(expoPushGateway.send(anyList())).thenReturn(List.of(new ExpoPushTicket("ok", "old-ticket", null, null)));
        service.notifyRecoveryEvent(createEvent(), "title", "body");
        device.updateRegistration(DevicePlatform.IOS, "ExpoPushToken[new]");
        when(expoPushGateway.getReceipts(List.of("old-ticket"))).thenReturn(Map.of("old-ticket",
                new ExpoPushReceipt("error", "unregistered", new ExpoPushTicket.ExpoErrorDetails("DeviceNotRegistered"))));

        service.checkPendingReceipts();

        assertThat(device.getExpoPushToken()).isEqualTo("ExpoPushToken[new]");
        assertThat(pendingReceipts.count()).isZero();
    }

    private Member setupRecipient(boolean enabled, String token) {
        Member member = memberRepository.saveAndFlush(new Member(null, null));
        settingRepository.saveAndFlush(new NotificationSetting(member, enabled));
        deviceRepository.saveAndFlush(new Device(member, UUID.randomUUID().toString(), DevicePlatform.IOS, token));
        return member;
    }

    private long createEvent() {
        Long trailId = insertId("INSERT INTO trails(name) VALUES ('test')");
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

    private Long insertId(String sql) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS), keys);
        return keys.getKey().longValue();
    }
}
