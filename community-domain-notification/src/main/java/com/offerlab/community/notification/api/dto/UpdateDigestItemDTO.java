package com.offerlab.community.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDigestItemDTO {
    private String projectionType;
    private String digestKey;
    private String dedupKey;
    private String eventId;
    private String eventType;
    private String sourceType;
    private String sourceId;
    private String title;
    private String summary;
    private String targetPath;
    private LocalDateTime occurredAt;
    private int occurrenceCount;
    private List<Long> notificationIds;
    private boolean notificationUnread;
    private RevisitStateDTO revisit;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevisitStateDTO {
        private Long itemId;
        private String status;
        private String targetPath;
        private LocalDateTime dueAt;
        private LocalDateTime updateTime;
    }
}
