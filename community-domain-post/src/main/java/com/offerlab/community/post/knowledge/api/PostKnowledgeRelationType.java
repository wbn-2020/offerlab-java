package com.offerlab.community.post.knowledge.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PostKnowledgeRelationType {
    DUPLICATE_OF("duplicate_of"),
    SUPERSEDES("supersedes"),
    CONTINUES("continues"),
    SUPPLEMENTS("supplements"),
    PREREQUISITE_OF("prerequisite_of"),
    CONTRADICTS("contradicts");

    private final String value;

    PostKnowledgeRelationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PostKnowledgeRelationType fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (PostKnowledgeRelationType type : values()) {
            if (type.name().equals(normalized) || type.value.toUpperCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown post knowledge relation type: " + value);
    }
}
