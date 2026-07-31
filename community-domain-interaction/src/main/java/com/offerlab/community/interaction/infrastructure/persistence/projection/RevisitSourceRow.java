package com.offerlab.community.interaction.infrastructure.persistence.projection;

import lombok.Data;

@Data
public class RevisitSourceRow {
    private String sourceType;
    private String sourceId;
    private String reasonType;
    private Long activityCursor;
    private String title;
    private String description;
    private String targetPath;
}
