package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgeRelationSource {
    MANUAL("manual"),
    TOPIC("topic"),
    SERIES("series"),
    SEARCH("search"),
    TAG("tag"),
    CURATION("curation");

    private final String value;

    KnowledgeRelationSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
