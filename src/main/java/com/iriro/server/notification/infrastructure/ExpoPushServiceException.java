package com.iriro.server.notification.infrastructure;

public class ExpoPushServiceException extends RuntimeException {
    public ExpoPushServiceException(String message) { super(message); }
    public ExpoPushServiceException(String message, Throwable cause) { super(message, cause); }
}
