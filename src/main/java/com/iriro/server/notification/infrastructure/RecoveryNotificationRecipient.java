package com.iriro.server.notification.infrastructure;

public interface RecoveryNotificationRecipient {
    Long getDeviceId();
    Long getMemberId();
    String getExpoPushToken();
}
