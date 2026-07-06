package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgeVisibilityState {
    VISIBLE("visible"),
    ARCHIVED("archived"),
    EXCLUDED("excluded"),
    DEGRADED("degraded");

    private final String value;

    KnowledgeVisibilityState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
