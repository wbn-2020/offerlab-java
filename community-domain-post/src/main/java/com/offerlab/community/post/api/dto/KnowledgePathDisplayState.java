package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgePathDisplayState {
    NORMAL("normal"),
    PARTIAL("partial"),
    DEGRADED("degraded");

    private final String value;

    KnowledgePathDisplayState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
