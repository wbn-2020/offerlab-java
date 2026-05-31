package com.offerlab.community.notification.application;

import java.util.Map;

final class NotificationDedupKey {

    private NotificationDedupKey() {
    }

    static String of(Long receiverUid, Long senderUid, Integer notifType,
                     Integer targetType, Long targetId, Map<String, Object> content) {
        String action = content == null ? null : String.valueOf(content.getOrDefault("action", ""));
        return String.join(":",
                String.valueOf(receiverUid),
                String.valueOf(senderUid),
                String.valueOf(notifType),
                String.valueOf(targetType),
                String.valueOf(targetId),
                action == null ? "" : action);
    }
}
