package com.offerlab.community.notification.realtime;

public final class NotificationProtocolException extends RuntimeException {

    public NotificationProtocolException(String message) {
        super(message);
    }

    public NotificationProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
