package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgeRelationType {
    BELONGS_TO("belongs_to"),
    REFERENCES("references"),
    CONTINUES("continues"),
    RELATED("related"),
    FILLS_GAP("fills_gap"),
    SEARCH_ENTRY("search_entry");

    private final String value;

    KnowledgeRelationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
