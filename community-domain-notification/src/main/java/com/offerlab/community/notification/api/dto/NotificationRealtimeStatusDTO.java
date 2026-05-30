package com.offerlab.community.notification.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class NotificationRealtimeStatusDTO {
    private Map<String, Long> unread;
    private Long latestUnreadId;
    private LocalDateTime latestUnreadAt;
    private long serverTime;
    private int pollIntervalSeconds;
}
