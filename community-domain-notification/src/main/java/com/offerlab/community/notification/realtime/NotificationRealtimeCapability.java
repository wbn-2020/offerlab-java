package com.offerlab.community.notification.realtime;

public interface NotificationRealtimeCapability {

    boolean isWebSocketAvailable();

    static NotificationRealtimeCapability unavailable() {
        return () -> false;
    }
}
