package com.offerlab.community.analytics.api.dto;

public record V45ApprovalDecisionDTO(
        String requestId,
        boolean approved,
        String reason,
        long decidedByUid) {
}
