package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgeGapSource {
    TOPIC("topic"),
    SEARCH("search"),
    SERIES("series"),
    MANUAL("manual");

    private final String value;

    KnowledgeGapSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
