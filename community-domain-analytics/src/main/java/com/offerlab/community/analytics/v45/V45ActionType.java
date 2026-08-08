package com.offerlab.community.analytics.v45;

import java.util.Locale;

public enum V45ActionType {
    RECOMMEND_PLAYBOOK,
    QUEUE_GOVERNANCE_REMINDER,
    ESCALATE_GOVERNANCE_ATTENTION,
    OPEN_BATCH_COORDINATION;

    public static V45ActionType parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return value.trim().toUpperCase(Locale.ROOT).isEmpty()
                    ? null
                    : value.trim().toUpperCase(Locale.ROOT).transform(V45ActionType::valueOf);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isMediumRisk() {
        return this == ESCALATE_GOVERNANCE_ATTENTION || this == OPEN_BATCH_COORDINATION;
    }

    public V45RiskLevel riskLevel() {
        return isMediumRisk() ? V45RiskLevel.MEDIUM : V45RiskLevel.LOW;
    }
}
