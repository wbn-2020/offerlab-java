package com.offerlab.community.interaction.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RevisitItemDTO {
    private Long id;
    private String sourceType;
    private String sourceId;
    private String reasonType;
    private Long activityCursor;
    private String title;
    private String description;
    private String targetPath;
    private String status;
    private LocalDateTime dueAt;
    private LocalDateTime snoozedUntil;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean onSiteOnly;
    private Boolean externalPush;
    private Boolean advertising;
    private Boolean payment;
}
