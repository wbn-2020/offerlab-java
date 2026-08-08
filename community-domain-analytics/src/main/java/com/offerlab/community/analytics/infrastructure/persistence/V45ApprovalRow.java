package com.offerlab.community.analytics.infrastructure.persistence;

import java.time.LocalDateTime;

public record V45ApprovalRow(
        Long id,
        String requestId,
        String actionType,
        String riskLevel,
        String status,
        Long requestedByUid,
        Long decidedByUid,
        String decisionReason,
        LocalDateTime decidedAt,
        Integer approvalVersion,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
