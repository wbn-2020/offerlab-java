package com.offerlab.community.analytics.v45;

public record V45GuardDecision(
        boolean allowed,
        boolean dryRun,
        boolean approvalRequired,
        String reason,
        V45ActionType actionType,
        V45RuleAst ruleAst) {

    public static V45GuardDecision reject(String reason) {
        return new V45GuardDecision(false, false, false, reason, null, null);
    }

    public static V45GuardDecision allowed(V45ActionType action, boolean approvalRequired, V45RuleAst ast) {
        return new V45GuardDecision(true, false, approvalRequired, "ALLOWED", action, ast);
    }

    public static V45GuardDecision dryRun(V45ActionType action, boolean approvalRequired, V45RuleAst ast) {
        return new V45GuardDecision(true, true, approvalRequired, "DRY_RUN_NO_SIDE_EFFECT", action, ast);
    }

    public static V45GuardDecision downstreamDisabled(
            V45ActionType action, boolean approvalRequired, V45RuleAst ast) {
        return new V45GuardDecision(false, false, approvalRequired,
                "DOWNSTREAM_ACTIONS_DISABLED", action, ast);
    }
}
