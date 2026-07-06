package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgeAssetType {
    POST("post"),
    SERIES("series"),
    COLLECTION("collection"),
    TOPIC("topic"),
    TAG("tag"),
    SEARCH_ENTRY("search_entry");

    private final String value;

    KnowledgeAssetType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
