package com.offerlab.community.notification.application;

enum SubscriptionUpdateNotificationKind {
    SYSTEM(5),
    DISCUSSION_FOLLOW_COMMENT(2),
    DISCUSSION_FOLLOW_QUALITY_COMMENT(2);

    private final int notificationType;

    SubscriptionUpdateNotificationKind(int notificationType) {
        this.notificationType = notificationType;
    }

    int notificationType() {
        return notificationType;
    }
}
