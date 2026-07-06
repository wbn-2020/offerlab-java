package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgePreviewSource {
    REMOTE("remote"),
    LOCAL("local"),
    FALLBACK("fallback"),
    DEMO("demo");

    private final String value;

    KnowledgePreviewSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
