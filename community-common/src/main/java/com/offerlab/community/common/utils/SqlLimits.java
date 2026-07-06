package com.offerlab.community.common.utils;

public final class SqlLimits {

    private SqlLimits() {
    }

    public static String limitOne() {
        return "LIMIT 1";
    }

    public static String limit(int value, int min, int max) {
        return "LIMIT " + clamp(value, min, max);
    }

    public static int clamp(int value, int min, int max) {
        int safeMin = Math.max(1, min);
        int safeMax = Math.max(safeMin, max);
        return Math.max(safeMin, Math.min(value, safeMax));
    }
}
