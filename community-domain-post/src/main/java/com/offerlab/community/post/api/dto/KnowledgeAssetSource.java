package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgeAssetSource {
    POST("post"),
    SERIES("series"),
    COLLECTION("collection"),
    TOPIC("topic"),
    TAG("tag"),
    SEARCH("search"),
    MANUAL("manual"),
    CURATION("curation");

    private final String value;

    KnowledgeAssetSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
