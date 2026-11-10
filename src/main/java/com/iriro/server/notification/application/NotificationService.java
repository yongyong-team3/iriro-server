package com.iriro.server.notification.application;

import com.iriro.server.common.application.ApiException;
import com.iriro.server.notification.domain.Notification;
import com.iriro.server.notification.infrastructure.NotificationRepository;
import com.iriro.server.notification.presentation.NotificationListResponse;
import com.iriro.server.notification.presentation.NotificationReadResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock = Clock.systemUTC();

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(Long memberId, Long beforeId, int limit) {
        List<Notification> results = beforeId == null
                ? notificationRepository.findByMember_IdOrderByIdDesc(memberId, PageRequest.of(0, limit + 1))
                : notificationRepository.findByMember_IdAndIdLessThanOrderByIdDesc(
                        memberId,
                        beforeId,
                        PageRequest.of(0, limit + 1)
                );
        boolean hasNext = results.size() > limit;
        List<Notification> page = hasNext ? results.subList(0, limit) : results;
        List<NotificationListResponse.NotificationItem> items = page.stream()
                .map(this::toItem)
                .toList();
        Long nextCursorId = hasNext && !page.isEmpty() ? page.getLast().getId() : null;
        return new NotificationListResponse(items, nextCursorId, hasNext);
    }

    @Transactional
    public NotificationReadResponse markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMember_Id(notificationId, memberId)
                .orElseThrow(this::notFound);
        notification.markRead(LocalDateTime.now(clock));
        return new NotificationReadResponse(notification.getId(), notification.getReadAt());
    }

    private NotificationListResponse.NotificationItem toItem(Notification notification) {
        return new NotificationListResponse.NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.");
    }
}
