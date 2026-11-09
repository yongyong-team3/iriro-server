package com.iriro.server.notification.application;

import java.util.Map;

public record RecoveryNotificationDispatchResult(
        int notificationsCreated,
        Map<String, Long> ticketDeviceIds,
        Map<String, String> ticketErrors
) { }
