package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgeAssetStatus {
    ACTIVE("active"),
    ARCHIVED("archived");

    private final String value;

    KnowledgeAssetStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
