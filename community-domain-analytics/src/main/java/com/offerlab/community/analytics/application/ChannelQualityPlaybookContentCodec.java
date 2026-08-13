package com.offerlab.community.analytics.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChannelQualityPlaybookContentCodec {

    public static final String CONTENT_SCHEMA_VERSION = "V44_PLAYBOOK_CONTENT_V1";
    public static final String INPUT_SCHEMA_VERSION = "V44_PLAYBOOK_INPUT_V1";
    public static final String CASE_INPUT_SCHEMA_VERSION = "V44_CASE_PLAYBOOK_INPUT_V1";
    private static final int MAX_CONTENT_BYTES = 64 * 1024;
    private static final Set<String> ACTION_TYPES = Set.of(
            "REVIEW_EVIDENCE", "CONTACT_CASE_OWNER", "REQUEST_GOVERNANCE_REVIEW",
            "OPEN_BATCH_COORDINATION", "SEND_GOVERNANCE_REMINDER",
            "ESCALATE_GOVERNANCE_ATTENTION", "RECORD_FOLLOW_UP", "CUSTOM_HUMAN_ACTION");
    private static final Set<String> EXECUTION_HINTS =
            Set.of("MANUAL_ONLY", "CONTROLLED_AUTOMATION_CANDIDATE");
    private static final Set<String> CONDITION_TYPES = Set.of(
            "ALL_REQUIRED_CHECKS_TERMINAL", "V41_ROOT_CAUSE_PRESENT",
            "V41_OUTCOME_PRESENT", "V41_LEARNING_CATEGORY_PRESENT",
            "V41_EVIDENCE_TYPE_PRESENT", "V41_ACTION_REFERENCE_PRESENT",
            "NO_ACCEPTED_PLAYBOOK_IN_PROGRESS");
    private static final Set<String> SEVERITIES = Set.of("BLOCKING", "ADVISORY");
    private static final Set<String> EVIDENCE_REQUIREMENTS =
            Set.of("NONE", "REFERENCE_REQUIRED");
    private static final Set<String> REFERENCE_TYPES =
            Set.of("INTERNAL_DOC", "INTERNAL_PAGE", "EXTERNAL_DOC");
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "contentSchemaVersion", "title", "description", "scope", "suggestedActions",
            "requiredChecks", "closeConditions", "references");

    private final ObjectMapper objectMapper;

    public EncodedContent encode(JsonNode content) {
        JsonNode normalized = normalize(content);
        String canonical;
        try {
            canonical = objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return new EncodedContent(
                CONTENT_SCHEMA_VERSION,
                canonical,
                "sha256:" + sha256(canonical),
                summary(normalized));
    }

    public void verify(String canonicalContentJson, String contentHash) {
        if (!StringUtils.hasText(canonicalContentJson)
                || !isSha256(contentHash)
                || !("sha256:" + sha256(canonicalContentJson)).equals(contentHash)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        try {
            EncodedContent encoded = encode(objectMapper.readTree(canonicalContentJson));
            if (!encoded.canonicalJson().equals(canonicalContentJson)
                    || !encoded.contentHash().equals(contentHash)) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    public String snapshotHash(JsonNode snapshot) {
        try {
            return "sha256:" + sha256(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private JsonNode normalize(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ObjectNode source = (ObjectNode) input;
        Set<String> unknown = new HashSet<>();
        source.fieldNames().forEachRemaining(field -> {
            if (!TOP_LEVEL_FIELDS.contains(field)) {
                unknown.add(field);
            }
        });
        if (!unknown.isEmpty() || !CONTENT_SCHEMA_VERSION.equals(text(source, "contentSchemaVersion"))) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        normalized.put("contentSchemaVersion", CONTENT_SCHEMA_VERSION);
        normalized.put("title", requiredText(source, "title", 2, 100));
        normalized.put("description", requiredText(source, "description", 2, 1000));
        normalized.set("scope", normalizeScope(source.get("scope")));
        normalized.set("suggestedActions", normalizeActions(source.get("suggestedActions")));
        normalized.set("requiredChecks", normalizeChecks(source.get("requiredChecks")));
        normalized.set("closeConditions", normalizeConditions(source.get("closeConditions")));
        normalized.set("references", normalizeReferences(source.get("references")));
        return normalized;
    }

    private ObjectNode normalizeScope(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ObjectNode source = (ObjectNode) input;
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        normalized.set("domains", intArray(source.get("domains"), 0, 5));
        normalized.set("triggerTypes", textArray(source.get("triggerTypes"), 0, 40));
        normalized.set("riskCodes", textArray(source.get("riskCodes"), 0, 64));
        normalized.set("rootCauseCategories", textArray(source.get("rootCauseCategories"), 0, 64));
        normalized.set("outcomeTypes", textArray(source.get("outcomeTypes"), 0, 64));
        normalized.set("contentRecoveryStates", textArray(source.get("contentRecoveryStates"), 0, 64));
        normalized.set("learningCategories", textArray(source.get("learningCategories"), 0, 64));
        normalized.set("recurrenceRelationTypes", textArray(source.get("recurrenceRelationTypes"), 0, 64));
        normalized.set("requiredFacts", textArray(source.get("requiredFacts"), 0, 64));
        return normalized;
    }

    private ArrayNode normalizeActions(JsonNode input) {
        if (input == null || !input.isArray() || input.size() < 1 || input.size() > 20) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : sortItems(input, "actionKey")) {
            ObjectNode source = object(item);
            ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            normalized.put("actionKey", requiredText(source, "actionKey", 2, 80));
            String type = requiredText(source, "actionType", 2, 64).toUpperCase(Locale.ROOT);
            String mode = requiredText(source, "executionModeHint", 2, 64).toUpperCase(Locale.ROOT);
            if (!ACTION_TYPES.contains(type) || !EXECUTION_HINTS.contains(mode)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            normalized.put("actionType", type);
            normalized.put("title", requiredText(source, "title", 2, 200));
            normalized.put("instruction", requiredText(source, "instruction", 2, 500));
            normalized.put("executionModeHint", mode);
            normalized.put("order", positiveOrder(source.get("order")));
            result.add(normalized);
        }
        requireUnique(result, "actionKey");
        return result;
    }

    private ArrayNode normalizeChecks(JsonNode input) {
        if (input == null || !input.isArray() || input.size() > 20) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : sortItems(input, "checkKey")) {
            ObjectNode source = object(item);
            ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            normalized.put("checkKey", requiredText(source, "checkKey", 2, 80));
            normalized.put("title", requiredText(source, "title", 2, 200));
            normalized.put("instruction", requiredText(source, "instruction", 2, 500));
            String requirement = requiredText(source, "evidenceRequirement", 2, 64)
                    .toUpperCase(Locale.ROOT);
            if (!EVIDENCE_REQUIREMENTS.contains(requirement)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            normalized.put("evidenceRequirement", requirement);
            normalized.set("allowedEvidenceTypes", textArray(source.get("allowedEvidenceTypes"), 0, 64));
            normalized.put("order", positiveOrder(source.get("order")));
            result.add(normalized);
        }
        requireUnique(result, "checkKey");
        return result;
    }

    private ArrayNode normalizeConditions(JsonNode input) {
        if (input == null || !input.isArray() || input.size() < 1 || input.size() > 10) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : sortItems(input, "conditionKey")) {
            ObjectNode source = object(item);
            ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            normalized.put("conditionKey", requiredText(source, "conditionKey", 2, 80));
            String type = requiredText(source, "conditionType", 2, 64).toUpperCase(Locale.ROOT);
            String severity = requiredText(source, "severity", 2, 32).toUpperCase(Locale.ROOT);
            if (!CONDITION_TYPES.contains(type) || !SEVERITIES.contains(severity)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            normalized.put("conditionType", type);
            normalized.put("severity", severity);
            normalized.set("parameters", source.path("parameters").isObject()
                    ? source.path("parameters") : JsonNodeFactory.instance.objectNode());
            normalized.put("order", positiveOrder(source.get("order")));
            result.add(normalized);
        }
        requireUnique(result, "conditionKey");
        return result;
    }

    private ArrayNode normalizeReferences(JsonNode input) {
        if (input == null || !input.isArray() || input.size() > 20) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : sortItems(input, "referenceKey")) {
            ObjectNode source = object(item);
            ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            normalized.put("referenceKey", requiredText(source, "referenceKey", 2, 80));
            String type = requiredText(source, "referenceType", 2, 32).toUpperCase(Locale.ROOT);
            String uri = requiredText(source, "uri", 2, 512);
            if (!REFERENCE_TYPES.contains(type)
                    || !(uri.startsWith("/") || uri.startsWith("https://"))) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            normalized.put("referenceType", type);
            normalized.put("title", requiredText(source, "title", 2, 200));
            normalized.put("uri", uri);
            normalized.put("summary", requiredText(source, "summary", 2, 500));
            normalized.put("order", positiveOrder(source.get("order")));
            result.add(normalized);
        }
        requireUnique(result, "referenceKey");
        return result;
    }

    private static ObjectNode object(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return (ObjectNode) input;
    }

    private static ArrayNode intArray(JsonNode input, int min, int max) {
        if (input == null || !input.isArray() || input.size() > max) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<Integer> values = new ArrayList<>();
        input.forEach(value -> {
            if (!value.isInt() || value.asInt() < 1 || value.asInt() > 5) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            values.add(value.asInt());
        });
        values.sort(Integer::compareTo);
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        values.forEach(result::add);
        return result;
    }

    private static ArrayNode textArray(JsonNode input, int min, int max) {
        if (input == null || !input.isArray() || input.size() > max) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Set<String> values = new HashSet<>();
        input.forEach(value -> {
            if (!value.isTextual() || !StringUtils.hasText(value.asText())
                    || value.asText().trim().length() > max) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            values.add(value.asText().trim().toUpperCase(Locale.ROOT));
        });
        if (values.size() < min) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        values.stream().sorted().forEach(result::add);
        return result;
    }

    private static List<JsonNode> sortItems(JsonNode input, String keyField) {
        if (input == null || !input.isArray()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<JsonNode> items = new ArrayList<>();
        input.forEach(items::add);
        items.sort(Comparator.comparingInt((JsonNode value) -> value.path("order").asInt(Integer.MAX_VALUE))
                .thenComparing(value -> value.path(keyField).asText()));
        return items;
    }

    private static void requireUnique(ArrayNode items, String field) {
        Set<String> keys = new HashSet<>();
        items.forEach(item -> {
            if (!keys.add(item.path(field).asText())) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
        });
    }

    private static int positiveOrder(JsonNode value) {
        if (value == null || !value.isInt() || value.asInt() < 1 || value.asInt() > 10000) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value.asInt();
    }

    private static String requiredText(ObjectNode node, String field, int min, int max) {
        String value = text(node, field);
        if (!StringUtils.hasText(value) || value.trim().length() < min || value.trim().length() > max) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value.trim();
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private static String summary(JsonNode content) {
        return content.path("title").asText()
                + " | actions=" + content.path("suggestedActions").size()
                + " | checks=" + content.path("requiredChecks").size()
                + " | conditions=" + content.path("closeConditions").size();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte current : digest) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("sha256:[0-9a-f]{64}");
    }

    public record EncodedContent(String schemaVersion, String canonicalJson, String contentHash, String summary) {
    }
}
