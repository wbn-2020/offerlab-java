package com.offerlab.community.analytics.api.dto;

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
public class ProjectionHealthDTO {
    private String projectionType;
    private String displayName;
    private String healthStatus;
    private Long issueCount;
    private Boolean issueCountCapped;
    private Boolean available;
    private Boolean reconciliationSupported;
    private Integer slaMinutes;
    private Long latestRunId;
    private Long watermark;
    private Long backlogCount;
    private Long overdueCount;
    private Long backlogAgeSeconds;
    private LocalDateTime oldestBacklogAt;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;
    private String repairMode;
    private LocalDateTime checkedAt;
    private List<String> attentionReasons;
}
