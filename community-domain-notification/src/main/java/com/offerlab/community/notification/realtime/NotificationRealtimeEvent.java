package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

public record NotificationRealtimeEvent(
        String instanceId,
        Long receiverUid,
        Long notificationId,
        Map<String, Long> unread
) {

    @JsonIgnore
    public boolean isValid() {
        return instanceId != null && !instanceId.isBlank()
                && receiverUid != null && receiverUid > 0
                && unread != null;
    }
}
