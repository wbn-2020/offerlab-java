package com.offerlab.community.notification.infrastructure.persistence.mapper;

import java.time.LocalDateTime;

public record NotificationAggregateWindow(
        String windowKey,
        Integer notifType,
        Integer targetType,
        Long targetId,
        LocalDateTime windowStart,
        LocalDateTime windowEnd) {
}
