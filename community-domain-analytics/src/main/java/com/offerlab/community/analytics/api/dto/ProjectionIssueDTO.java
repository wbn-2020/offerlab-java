package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionIssueDTO {
    private Long issueId;
    private String projectionType;
    private String issueType;
    private String severity;
    private String subjectType;
    private String subjectId;
    private String summary;
    private LocalDateTime detectedAt;
    private Long relatedPostId;
    private Integer domain;
}
