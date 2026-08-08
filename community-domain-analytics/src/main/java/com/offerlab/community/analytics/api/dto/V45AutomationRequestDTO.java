package com.offerlab.community.analytics.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record V45AutomationRequestDTO(
        String ruleId,
        String actionType,
        JsonNode rule,
        boolean dryRun,
        String idempotencyKey,
        long requestedByUid) {
}
