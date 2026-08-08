package com.offerlab.community.analytics.infrastructure.persistence;

import java.time.LocalDateTime;

public record V45KillSwitchRow(
        Long id,
        String switchKey,
        Boolean enabled,
        String reason,
        Long updatedByUid,
        Integer switchVersion,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
