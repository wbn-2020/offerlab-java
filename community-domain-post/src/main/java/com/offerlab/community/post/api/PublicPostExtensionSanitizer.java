package com.offerlab.community.post.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * Keeps only product metadata that is intentionally part of the public post contract.
 */
public final class PublicPostExtensionSanitizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PUBLIC_FIELDS = Set.of(
            "domain",
            "anonymous",
            "summary",
            "company",
            "position",
            "yearsOfExp",
            "interviewResult",
            "difficulty",
            "scenario",
            "contentType",
            "techStacks",
            "featured",
            "coverUrl",
            "imageUrl",
            "seriesId",
            "seriesTitle",
            "contentSeriesId",
            "contentSeriesTitle",
            "topic",
            "topicName",
            "topicNames",
            "topicSlug",
            "topicTitle",
            "curatedTopicSlug",
            "curatedTopicTitle",
            "aiTags",
            "suggestedTags",
            "faqJson",
            "knowledgeCardJson",
            "suggestionsOpen",
            "contentSuggestionsOpen"
    );

    private PublicPostExtensionSanitizer() {
    }

    public static String sanitize(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return null;
        }
        try {
            JsonNode source = JSON.readTree(extJson);
            if (!source.isObject()) {
                return null;
            }
            ObjectNode publicExtension = JSON.createObjectNode();
            PUBLIC_FIELDS.forEach(field -> {
                JsonNode value = source.get(field);
                if (value != null && !value.isNull()) {
                    publicExtension.set(field, value);
                }
            });
            return publicExtension.isEmpty() ? null : JSON.writeValueAsString(publicExtension);
        } catch (Exception ignored) {
            return null;
        }
    }
}
