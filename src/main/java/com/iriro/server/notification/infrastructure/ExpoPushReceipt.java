package com.iriro.server.notification.infrastructure;

public record ExpoPushReceipt(String status, String message, ExpoPushTicket.ExpoErrorDetails details) { }
