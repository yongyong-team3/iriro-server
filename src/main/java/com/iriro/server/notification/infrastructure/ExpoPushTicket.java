package com.iriro.server.notification.infrastructure;

public record ExpoPushTicket(String status, String id, String message, ExpoErrorDetails details) {
    public record ExpoErrorDetails(String error) { }
}
