package com.offerlab.community.analytics.infrastructure.persistence;

import java.time.LocalDateTime;

public record V45QuotaCounterRow(
        Long id,
        String quotaKey,
        String actionType,
        String windowStart,
        Integer usedCount,
        Integer limitCount,
        Integer quotaVersion,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
