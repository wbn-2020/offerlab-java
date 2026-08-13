package com.offerlab.community.analytics.v45;

import com.offerlab.community.analytics.api.dto.V45AutomationRequestDTO;

import java.util.Locale;
import java.util.Set;

public final class V45ActionGuard {

    public static final boolean DOWNSTREAM_ACTIONS_ENABLED = false;
    private static final Set<String> FORBIDDEN_TOKENS = Set.of(
            "AUTO_CLOSE", "CLOSE_CASE", "PUNISH", "PENALTY", "PUBLISH_CONTENT",
            "MAINTENANCE_TASK", "V39", "COMMAND");

    private V45ActionGuard() {
    }

    public static V45GuardDecision check(V45AutomationRequestDTO request) {
        if (request == null || request.rule() == null || request.actionType() == null
                || request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            return V45GuardDecision.reject("INVALID_REQUEST");
        }
        V45ActionType action = V45ActionType.parse(request.actionType());
        if (action == null || containsForbiddenToken(request.actionType())
                || containsForbiddenToken(request.rule().toString())) {
            return V45GuardDecision.reject("UNKNOWN_OR_FORBIDDEN_ACTION");
        }
        V45RuleAst ast;
        try {
            ast = V45RuleAstCodec.decode(request.rule());
        } catch (RuntimeException ex) {
            return V45GuardDecision.reject("RULE_AST_FAIL_CLOSED");
        }
        if (V45ActionType.parse(request.actionType()) != action
                || !request.rule().path("actionType").asText("").equals(action.name())) {
            return V45GuardDecision.reject("ACTION_RULE_MISMATCH");
        }
        boolean approvalRequired = action.isMediumRisk();
        if (request.dryRun()) {
            return V45GuardDecision.dryRun(action, approvalRequired, ast);
        }
        return V45GuardDecision.downstreamDisabled(action, approvalRequired, ast);
    }

    private static boolean containsForbiddenToken(String value) {
        String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
        return FORBIDDEN_TOKENS.stream().anyMatch(normalized::contains);
    }
}
