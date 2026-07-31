package com.offerlab.community.incentive.domain;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;

import java.util.Locale;
import java.util.Set;

public final class IncentiveTypes {
    public static final String GLOBAL_DOMAIN = "GLOBAL";
    public static final int MAX_LIST_LIMIT = 100;
    public static final long MAX_SINGLE_DELTA = 1_000_000L;
    public static final int DEFAULT_DAILY_THANK_TICKETS = 3;
    public static final int MAX_BOUNTY_QUOTA = 500;
    public static final long MAX_BOUNTY_TOTAL_BUDGET = 100_000L;
    public static final long PLATFORM_BOUNTY_PERIOD_BUDGET = 1_000_000L;
    public static final long USER_BOUNTY_PERIOD_BUDGET = 5_000L;
    public static final Set<String> ACCOUNT_TYPES = Set.of("REPUTATION", "POINT");
    public static final Set<String> ORDER_STATUSES =
            Set.of("CREATED", "RESERVED", "DELIVERED", "CANCELLED", "REFUNDED");
    public static final Set<String> ROLE_GRANT_STATUSES =
            Set.of("ACTIVE", "SUSPENDED", "REVOKED", "EXPIRED");
    public static final Set<String> BENEFIT_CATEGORIES =
            Set.of("PROFILE_STYLE", "CONTENT_TOOL", "COMMEMORATION", "COMMUNITY_TOOL");
    public static final Set<String> BENEFIT_DELIVERY_TYPES =
            Set.of("ACCOUNT_ENTITLEMENT", "MANUAL", "REVERSIBLE");

    private IncentiveTypes() {
    }

    public static String requireAccountType(String value) {
        String normalized = upper(value);
        if (!ACCOUNT_TYPES.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "accountType must be REPUTATION or POINT");
        }
        return normalized;
    }

    public static String normalizeDomain(String value, String accountType) {
        String normalized = upper(value);
        if ("POINT".equals(accountType)) {
            if (normalized != null && !GLOBAL_DOMAIN.equals(normalized)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "POINT accounts are global only");
            }
            return GLOBAL_DOMAIN;
        }
        if (normalized == null || normalized.length() > 32) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "domainCode is required for reputation");
        }
        return normalized;
    }

    public static String requireReason(String value) {
        String normalized = clean(value, 500);
        if (normalized == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "reason is required");
        }
        return normalized;
    }

    public static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    public static String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public static String upper(String value) {
        String normalized = clean(value, 64);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    public static int safeLimit(Integer limit) {
        int requested = limit == null || limit <= 0 ? 20 : limit;
        return Math.min(requested, MAX_LIST_LIMIT);
    }

    public static long requirePositive(Long value, String field) {
        if (value == null || value <= 0 || value > MAX_SINGLE_DELTA) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), field + " must be positive");
        }
        return value;
    }
}
