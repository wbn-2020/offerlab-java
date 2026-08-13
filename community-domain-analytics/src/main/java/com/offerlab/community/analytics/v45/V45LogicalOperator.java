package com.offerlab.community.analytics.v45;

import java.util.Locale;

public enum V45LogicalOperator {
    ALL,
    ANY;

    public static V45LogicalOperator parse(String value) {
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
