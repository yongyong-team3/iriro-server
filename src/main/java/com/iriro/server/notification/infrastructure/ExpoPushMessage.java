package com.iriro.server.notification.infrastructure;

public record ExpoPushMessage(String to, String title, String body, Object data) { }
