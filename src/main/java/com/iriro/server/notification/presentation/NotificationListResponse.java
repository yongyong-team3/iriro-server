package com.iriro.server.notification.presentation;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationListResponse(
        List<NotificationItem> items,
        Long nextCursorId,
        boolean hasNext
) {

    public NotificationListResponse {
        items = List.copyOf(items);
    }

    public record NotificationItem(
            Long notificationId,
            String type,
            String title,
            String body,
            LocalDateTime createdAt,
            LocalDateTime readAt
    ) {
    }
}
