package com.offerlab.community.notification.realtime;

public final class WebSocketNotificationRealtimeCapability implements NotificationRealtimeCapability {

    @Override
    public boolean isWebSocketAvailable() {
        return true;
    }
}
