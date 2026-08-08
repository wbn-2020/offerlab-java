package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQualityReviewBatchCoordinationDTO {
    private Long batchId;
    private Integer domain;
    private String name;
    private Long dispatchAssigneeUid;
    private Instant dueAt;
    private Instant effectiveDueAt;
    private Integer coordinationVersion;
    private String progressState;
    private String dueState;
    private Integer openTaskCount;
    private Integer activeTaskCount;
    private Integer reassignableTaskCount;
    private Boolean canExtendDueAt;
    private Boolean canBulkReassign;
    private Boolean canAddRiskNote;
    private Boolean canWithdrawOpenTasks;
    private Map<String, Integer> taskStatusCounts;
    private List<ChannelQualityReviewBatchCoordinationTaskDTO> tasks;
}
