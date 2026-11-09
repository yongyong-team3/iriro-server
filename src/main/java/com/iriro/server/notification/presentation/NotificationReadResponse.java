package com.iriro.server.notification.presentation;

import java.time.LocalDateTime;

public record NotificationReadResponse(Long notificationId, LocalDateTime readAt) {
}
