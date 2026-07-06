package com.offerlab.community.post.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KnowledgeGapTargetStage {
    WORKSPACE("workspace"),
    EDITOR("editor"),
    TOPIC("topic"),
    KNOWLEDGE("knowledge");

    private final String value;

    KnowledgeGapTargetStage(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
