package com.offerlab.community.analytics.v45;

import java.util.Set;

public final class V45StaticBoundaryGuard {

    private static final Set<String> FORBIDDEN_DEPENDENCY_NAMES = Set.of(
            "V39", "V40", "V42", "V44", "COMMAND", "ADAPTER", "FACADE", "SERVICE");

    private V45StaticBoundaryGuard() {
    }

    public static boolean isPureV45TypeName(String typeName) {
        if (typeName == null || !typeName.startsWith("com.offerlab.community.analytics.v45.")) {
            return false;
        }
        String upper = typeName.toUpperCase(java.util.Locale.ROOT);
        return FORBIDDEN_DEPENDENCY_NAMES.stream().noneMatch(upper::contains);
    }

    public static boolean isDownstreamActionAllowed(V45ActionType actionType) {
        return actionType != null && Set.of(V45ActionType.values()).contains(actionType);
    }
}
