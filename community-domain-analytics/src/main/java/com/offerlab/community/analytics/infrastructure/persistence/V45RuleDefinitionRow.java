package com.offerlab.community.analytics.infrastructure.persistence;

import java.time.LocalDateTime;

public record V45RuleDefinitionRow(
        Long id,
        String ruleCode,
        Integer versionNo,
        String astSchemaVersion,
        String canonicalAstJson,
        String astHash,
        String actionType,
        String riskLevel,
        Boolean enabled,
        Long createdByUid,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
