package com.offerlab.community.analytics.v45;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class V45RuleAstCodec {

    public static final String SCHEMA_VERSION = "V45_RULE_AST_V1";
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "root", "actionType");
    private static final Set<String> LOGICAL_FIELDS = Set.of("kind", "operator", "children");
    private static final Set<String> PREDICATE_FIELDS = Set.of("kind", "field", "operator", "value", "values");

    private V45RuleAstCodec() {
    }

    public static Encoded encode(ObjectMapper mapper, JsonNode input) {
        ObjectNode normalized = normalizeRoot(input);
        String canonical;
        try {
            canonical = mapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("V45 rule cannot be encoded", ex);
        }
        return new Encoded(canonical, "sha256:" + sha256(canonical));
    }

    public static V45RuleAst decode(JsonNode input) {
        return decodeNode(normalizeRoot(input).get("root"));
    }

    private static ObjectNode normalizeRoot(JsonNode input) {
        ObjectNode source = object(input);
        requireExactFields(source, ROOT_FIELDS);
        V45ActionType action = V45ActionType.parse(text(source, "actionType"));
        if (!SCHEMA_VERSION.equals(text(source, "schemaVersion")) || action == null) {
            throw new IllegalArgumentException("V45 rule root is not allow-listed");
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("schemaVersion", SCHEMA_VERSION);
        result.set("root", normalizeNode(source.get("root")));
        result.put("actionType", action.name());
        return result;
    }

    private static JsonNode normalizeNode(JsonNode input) {
        ObjectNode source = object(input);
        String kind = text(source, "kind");
        if ("predicate".equals(kind)) {
            requireAllowedFields(source, PREDICATE_FIELDS);
            requireFields(source, Set.of("kind", "field", "operator"));
            V45RuleField field = V45RuleField.parse(text(source, "field"));
            V45RuleOperator operator = V45RuleOperator.parse(text(source, "operator"));
            if (field == null || operator == null) {
                throw new IllegalArgumentException("V45 predicate is not allow-listed");
            }
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.put("kind", "predicate");
            result.put("field", field.name());
            result.put("operator", operator.name());
            if (operator == V45RuleOperator.EXISTS) {
                if (source.has("value") || source.has("values")) {
                    throw new IllegalArgumentException("V45 EXISTS predicate has a value");
                }
            } else if (operator == V45RuleOperator.IN || operator == V45RuleOperator.NOT_IN) {
                if (source.has("value") || !source.has("values") || !source.get("values").isArray()) {
                    throw new IllegalArgumentException("V45 set predicate is malformed");
                }
                result.set("values", normalizeValues((ArrayNode) source.get("values")));
            } else {
                if (!source.has("value") || !source.get("value").isTextual()
                        || source.get("value").asText().isBlank() || source.has("values")) {
                    throw new IllegalArgumentException("V45 scalar predicate is malformed");
                }
                result.put("value", source.get("value").asText().trim());
            }
            return result;
        }
        if ("logical".equals(kind)) {
            requireExactFields(source, LOGICAL_FIELDS);
            V45LogicalOperator operator = V45LogicalOperator.parse(text(source, "operator"));
            if (operator == null || !source.get("children").isArray()
                    || source.get("children").size() == 0 || source.get("children").size() > 16) {
                throw new IllegalArgumentException("V45 logical node is malformed");
            }
            List<JsonNode> children = new ArrayList<>();
            source.get("children").forEach(children::add);
            children.sort(Comparator.comparing(V45RuleAstCodec::stableNodeKey));
            ArrayNode normalizedChildren = JsonNodeFactory.instance.arrayNode();
            children.forEach(child -> normalizedChildren.add(normalizeNode(child)));
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.put("kind", "logical");
            result.put("operator", operator.name());
            result.set("children", normalizedChildren);
            return result;
        }
        throw new IllegalArgumentException("V45 rule node kind is not allow-listed");
    }

    private static V45RuleAst decodeNode(JsonNode input) {
        ObjectNode source = object(input);
        String kind = text(source, "kind");
        if ("predicate".equals(kind)) {
            V45RuleField field = V45RuleField.parse(text(source, "field"));
            V45RuleOperator operator = V45RuleOperator.parse(text(source, "operator"));
            if (field == null || operator == null) {
                throw new IllegalArgumentException("V45 predicate is not allow-listed");
            }
            if (operator == V45RuleOperator.IN || operator == V45RuleOperator.NOT_IN) {
                ArrayNode values = array(source.get("values"));
                List<String> normalized = new ArrayList<>();
                values.forEach(value -> {
                    if (!value.isTextual() || value.asText().isBlank()) {
                        throw new IllegalArgumentException("V45 set value is malformed");
                    }
                    normalized.add(value.asText().trim());
                });
                return new V45RuleAst.Predicate(field, operator, null, normalized);
            }
            return new V45RuleAst.Predicate(field, operator,
                    operator == V45RuleOperator.EXISTS ? null : text(source, "value"), List.of());
        }
        if ("logical".equals(kind)) {
            V45LogicalOperator operator = V45LogicalOperator.parse(text(source, "operator"));
            ArrayNode children = array(source.get("children"));
            List<V45RuleAst> decoded = new ArrayList<>();
            children.forEach(child -> decoded.add(decodeNode(child)));
            return new V45RuleAst.Logical(operator, decoded);
        }
        throw new IllegalArgumentException("V45 rule node kind is not allow-listed");
    }

    private static ArrayNode normalizeValues(ArrayNode input) {
        if (input.size() == 0 || input.size() > 32) {
            throw new IllegalArgumentException("V45 value set is outside the bound");
        }
        Set<String> values = new HashSet<>();
        input.forEach(value -> {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalArgumentException("V45 value set contains an invalid value");
            }
            values.add(value.asText().trim());
        });
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        values.stream().sorted().forEach(result::add);
        return result;
    }

    private static String stableNodeKey(JsonNode node) {
        return node.toString();
    }

    private static ObjectNode object(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("V45 rule node must be an object");
        }
        return (ObjectNode) input;
    }

    private static ArrayNode array(JsonNode input) {
        if (input == null || !input.isArray()) {
            throw new IllegalArgumentException("V45 rule children must be an array");
        }
        return (ArrayNode) input;
    }

    private static void requireExactFields(ObjectNode node, Set<String> allowed) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(allowed)) {
            throw new IllegalArgumentException("V45 rule contains unknown or missing fields");
        }
    }

    private static void requireAllowedFields(ObjectNode node, Set<String> allowed) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("V45 rule contains an unknown field");
            }
        });
    }

    private static void requireFields(ObjectNode node, Set<String> required) {
        if (!node.fieldNames().hasNext()) {
            throw new IllegalArgumentException("V45 rule is missing required fields");
        }
        for (String field : required) {
            if (!node.has(field)) {
                throw new IllegalArgumentException("V45 rule is missing a required field");
            }
        }
    }

    private static String text(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
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

    public record Encoded(String canonicalJson, String contentHash) {
    }
}
