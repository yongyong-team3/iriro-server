package com.iriro.server.notification.application;

import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.notification.domain.Device;
import com.iriro.server.notification.domain.Notification;
import com.iriro.server.notification.domain.RecoveryEvent;
import com.iriro.server.notification.domain.NotificationSetting;
import com.iriro.server.notification.domain.PendingPushReceipt;
import com.iriro.server.notification.infrastructure.DeviceRepository;
import com.iriro.server.notification.infrastructure.ExpoPushGateway;
import com.iriro.server.notification.infrastructure.ExpoPushMessage;
import com.iriro.server.notification.infrastructure.ExpoPushReceipt;
import com.iriro.server.notification.infrastructure.ExpoPushTicket;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import com.iriro.server.notification.infrastructure.NotificationSettingRepository;
import com.iriro.server.notification.infrastructure.PendingPushReceiptRepository;
import com.iriro.server.notification.infrastructure.RecoveryEventRepository;
import com.iriro.server.notification.infrastructure.RecoveryNotificationRecipient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RecoveryNotificationService {
    private final RecoveryEventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository settingRepository;
    private final PendingPushReceiptRepository pendingReceipts;
    private final MemberRepository memberRepository;
    private final ExpoPushGateway expoPushGateway;

    public RecoveryNotificationService(RecoveryEventRepository eventRepository,
                                       DeviceRepository deviceRepository,
                                       NotificationRepository notificationRepository,
                                       NotificationSettingRepository settingRepository,
                                       PendingPushReceiptRepository pendingReceipts,
                                       MemberRepository memberRepository,
                                       ExpoPushGateway expoPushGateway) {
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.notificationRepository = notificationRepository;
        this.settingRepository = settingRepository;
        this.pendingReceipts = pendingReceipts;
        this.memberRepository = memberRepository;
        this.expoPushGateway = expoPushGateway;
    }

    /** Called by the recovery-event producer after it has created the event. */
    @Transactional
    public RecoveryNotificationDispatchResult notifyRecoveryEvent(Long eventId, String title, String body) {
        RecoveryEvent event = eventRepository.findLockedById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Recovery event not found: " + eventId));
        if (event.getFirstNotifiedAt() != null) {
            return new RecoveryNotificationDispatchResult(0, Map.of(), Map.of());
        }
        List<RecoveryNotificationRecipient> recipients = deviceRepository.findRecoveryRecipients();

        Map<Long, List<RecoveryNotificationRecipient>> byMember = new LinkedHashMap<>();
        recipients.forEach(recipient -> byMember.computeIfAbsent(recipient.getMemberId(), ignored -> new ArrayList<>()).add(recipient));
        List<RecoveryNotificationRecipient> sendable = new ArrayList<>();
        int created = 0;
        for (NotificationSetting setting : settingRepository.findByRecoveryForecastEnabledTrue()) {
            Long memberId = setting.getMember().getId();
            if (notificationRepository.existsByEventIdAndMember_Id(eventId, memberId)) continue;
            notificationRepository.save(new Notification(
                    memberRepository.getReferenceById(memberId), eventId,
                    "RECOVERY_FORECAST", title, body));
            created++;
            sendable.addAll(byMember.getOrDefault(memberId, List.of()));
        }
        event.markFirstNotified(LocalDateTime.now(Clock.systemUTC()));

        List<ExpoPushMessage> messages = sendable.stream()
                .map(recipient -> new ExpoPushMessage(recipient.getExpoPushToken(), title, body,
                        Map.of("type", "RECOVERY_FORECAST", "eventId", eventId, "trailId", event.getTrailId())))
                .toList();
        List<ExpoPushTicket> tickets = messages.isEmpty() ? List.of() : expoPushGateway.send(messages);
        Map<String, Long> ticketDeviceIds = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < tickets.size(); i++) {
            ExpoPushTicket ticket = tickets.get(i);
            Device device = deviceRepository.findById(sendable.get(i).getDeviceId()).orElse(null);
            String error = errorName(ticket.details());
            if ("ok".equals(ticket.status()) && ticket.id() != null) {
                ticketDeviceIds.put(ticket.id(), sendable.get(i).getDeviceId());
                pendingReceipts.save(new PendingPushReceipt(ticket.id(), sendable.get(i).getDeviceId(),
                        sendable.get(i).getExpoPushToken()));
            } else {
                errors.put(String.valueOf(sendable.get(i).getDeviceId()),
                        ticket.message() == null ? "Expo ticket rejected" : ticket.message());
            }
            if ("DeviceNotRegistered".equals(error) && device != null
                    && sendable.get(i).getExpoPushToken().equals(device.getExpoPushToken())) {
                device.deactivatePushToken();
            }
        }
        return new RecoveryNotificationDispatchResult(created, Map.copyOf(ticketDeviceIds), Map.copyOf(errors));
    }

    /** Checks tickets from earlier polls; tickets without a ready receipt remain pending. */
    @Transactional
    public Map<String, ExpoPushReceipt> checkPendingReceipts() {
        List<PendingPushReceipt> pending = pendingReceipts.findAll();
        if (pending.isEmpty()) return Map.of();
        Map<String, ExpoPushReceipt> receipts = expoPushGateway.getReceipts(
                pending.stream().map(PendingPushReceipt::getTicketId).toList());
        Map<String, PendingPushReceipt> byTicket = new LinkedHashMap<>();
        pending.forEach(record -> byTicket.put(record.getTicketId(), record));
        receipts.forEach((ticketId, receipt) -> {
            PendingPushReceipt record = byTicket.get(ticketId);
            if (record == null) return;
            if ("error".equals(receipt.status()) && "DeviceNotRegistered".equals(errorName(receipt.details()))) {
                deviceRepository.findById(record.getDeviceId()).ifPresent(device -> {
                    if (record.getPushToken().equals(device.getExpoPushToken())) device.deactivatePushToken();
                });
            }
            pendingReceipts.delete(record);
        });
        return receipts;
    }

    private String errorName(ExpoPushTicket.ExpoErrorDetails details) {
        return details == null ? null : details.error();
    }
}
