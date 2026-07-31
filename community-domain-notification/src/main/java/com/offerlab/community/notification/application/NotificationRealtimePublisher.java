package com.offerlab.community.notification.application;

import java.util.Map;

/**
 * Publishes already-committed notification state to connected clients.
 */
public interface NotificationRealtimePublisher {

    void publishNotification(Long receiverUid, Long notificationId, Map<String, Long> unread);

    void publishUnreadCount(Long receiverUid, Map<String, Long> unread);
}
