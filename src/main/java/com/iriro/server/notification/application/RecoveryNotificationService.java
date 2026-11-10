package com.iriro.server.notification.application;

import com.iriro.server.member.infrastructure.MemberRepository;
import com.iriro.server.notification.domain.Device;
import com.iriro.server.notification.domain.Notification;
import com.iriro.server.notification.domain.RecoveryEvent;
import com.iriro.server.notification.infrastructure.DeviceRepository;
import com.iriro.server.notification.infrastructure.ExpoPushGateway;
import com.iriro.server.notification.infrastructure.ExpoPushMessage;
import com.iriro.server.notification.infrastructure.ExpoPushReceipt;
import com.iriro.server.notification.infrastructure.ExpoPushTicket;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import com.iriro.server.notification.infrastructure.RecoveryEventRepository;
import com.iriro.server.notification.infrastructure.RecoveryNotificationRecipient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecoveryNotificationService {
    private final RecoveryEventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final ExpoPushGateway expoPushGateway;

    public RecoveryNotificationService(RecoveryEventRepository eventRepository,
                                       DeviceRepository deviceRepository,
                                       NotificationRepository notificationRepository,
                                       MemberRepository memberRepository,
                                       ExpoPushGateway expoPushGateway) {
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.expoPushGateway = expoPushGateway;
    }

    /** Called by the recovery-event producer after it has created the event. */
    @Transactional
    public RecoveryNotificationDispatchResult notifyRecoveryEvent(Long eventId, String title, String body) {
        RecoveryEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Recovery event not found: " + eventId));
        List<RecoveryNotificationRecipient> recipients = deviceRepository.findRecoveryRecipients(event.getTrailId());

        Map<Long, List<RecoveryNotificationRecipient>> byMember = new LinkedHashMap<>();
        recipients.forEach(recipient -> byMember.computeIfAbsent(recipient.getMemberId(), ignored -> new ArrayList<>()).add(recipient));
        List<RecoveryNotificationRecipient> sendable = new ArrayList<>();
        int created = 0;
        for (Map.Entry<Long, List<RecoveryNotificationRecipient>> entry : byMember.entrySet()) {
            if (notificationRepository.existsByEventIdAndMember_Id(eventId, entry.getKey())) continue;
            notificationRepository.save(new Notification(
                    memberRepository.getReferenceById(entry.getKey()), eventId,
                    "RECOVERY_FORECAST", title, body));
            created++;
            sendable.addAll(entry.getValue());
        }

        List<ExpoPushMessage> messages = sendable.stream()
                .map(recipient -> new ExpoPushMessage(recipient.getExpoPushToken(), title, body,
                        Map.of("type", "RECOVERY_FORECAST", "eventId", eventId)))
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
            } else {
                errors.put(String.valueOf(sendable.get(i).getDeviceId()),
                        ticket.message() == null ? "Expo ticket rejected" : ticket.message());
            }
            if ("DeviceNotRegistered".equals(error) && device != null) device.deactivatePushToken();
        }
        return new RecoveryNotificationDispatchResult(created, Map.copyOf(ticketDeviceIds), Map.copyOf(errors));
    }

    /** Call later with the ticket-to-device mapping returned by notifyRecoveryEvent. */
    @Transactional
    public Map<String, ExpoPushReceipt> checkReceipts(Map<String, Long> ticketDeviceIds) {
        if (ticketDeviceIds.isEmpty()) return Map.of();
        Map<String, ExpoPushReceipt> receipts = expoPushGateway.getReceipts(List.copyOf(ticketDeviceIds.keySet()));
        receipts.forEach((ticketId, receipt) -> {
            if (!"error".equals(receipt.status()) || !"DeviceNotRegistered".equals(errorName(receipt.details()))) return;
            Long deviceId = ticketDeviceIds.get(ticketId);
            if (deviceId != null) deviceRepository.findById(deviceId).ifPresent(Device::deactivatePushToken);
        });
        return receipts;
    }

    private String errorName(ExpoPushTicket.ExpoErrorDetails details) {
        return details == null ? null : details.error();
    }
}
