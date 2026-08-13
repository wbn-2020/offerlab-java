package com.offerlab.community.post.api;

public record ContentMaintenanceBatchTaskCoordinationResult(
        int openTaskCount,
        int activeTaskCount,
        int affectedTaskCount,
        Long previousAssigneeUid) {
}
