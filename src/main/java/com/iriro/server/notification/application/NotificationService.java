package com.iriro.server.notification.application;

import com.iriro.server.common.application.ApiException;
import com.iriro.server.notification.domain.Notification;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import com.iriro.server.notification.infrastructure.RecoveryEventRepository;
import com.iriro.server.notification.presentation.NotificationListResponse;
import com.iriro.server.notification.presentation.NotificationReadResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RecoveryEventRepository eventRepository;
    private final Clock clock = Clock.systemUTC();
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public NotificationService(NotificationRepository notificationRepository, RecoveryEventRepository eventRepository) {
        this.notificationRepository = notificationRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long memberId, boolean unreadOnly, int page, int size) {
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByMember_IdAndTypeAndReadAtIsNullOrderByIdDesc(
                        memberId, "RECOVERY_FORECAST", PageRequest.of(page, size))
                : notificationRepository.findByMember_IdAndTypeOrderByIdDesc(
                        memberId, "RECOVERY_FORECAST", PageRequest.of(page, size));
        Map<Long, Long> trailIds = eventRepository.findAllById(result.getContent().stream()
                        .map(Notification::getEventId).distinct().toList()).stream()
                .collect(Collectors.toMap(event -> event.getId(), event -> event.getTrailId()));
        List<NotificationListResponse.NotificationItem> items = result.getContent().stream()
                .map(notification -> toItem(notification, trailIds.get(notification.getEventId())))
                .toList();
        return new NotificationListResponse(items, page, size, result.getTotalElements(),
                result.getTotalPages(), result.hasNext());
    }

    @Transactional
    public NotificationReadResponse markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMember_Id(notificationId, memberId)
                .orElseThrow(this::notFound);
        notification.markRead(LocalDateTime.now(clock));
        return new NotificationReadResponse(notification.getId(), inKorea(notification.getReadAt()));
    }

    private NotificationListResponse.NotificationItem toItem(Notification notification, Long trailId) {
        return new NotificationListResponse.NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                trailId,
                inKorea(notification.getCreatedAt()),
                inKorea(notification.getReadAt())
        );
    }

    private OffsetDateTime inKorea(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA).toOffsetDateTime();
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.");
    }
}
