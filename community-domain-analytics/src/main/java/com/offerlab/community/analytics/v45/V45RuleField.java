package com.offerlab.community.analytics.v45;

import java.util.Locale;

public enum V45RuleField {
    RISK_SCORE,
    RISK_LEVEL,
    CASE_STATUS,
    CASE_DOMAIN,
    TODO_STATUS,
    OVERDUE_MINUTES,
    PLAYBOOK_APPLICABILITY,
    REVIEW_BATCH_STATUS,
    OWNER_PRESENT,
    GOVERNANCE_SNAPSHOT_VERSION;

    public static V45RuleField parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
