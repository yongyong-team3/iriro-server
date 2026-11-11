package com.iriro.server.notification.presentation;

import java.time.OffsetDateTime;
import java.util.List;

public record NotificationListResponse(
        List<NotificationItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public NotificationListResponse {
        content = List.copyOf(content);
    }

    public record NotificationItem(
            Long notificationId,
            String type,
            String title,
            String body,
            Long trailId,
            OffsetDateTime createdAt,
            OffsetDateTime readAt
    ) {
    }
}
